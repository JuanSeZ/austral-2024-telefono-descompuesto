package ar.edu.austral.inf.sd

import ar.edu.austral.inf.sd.server.api.PlayApiService
import ar.edu.austral.inf.sd.server.api.RegisterNodeApiService
import ar.edu.austral.inf.sd.server.api.RelayApiService
import ar.edu.austral.inf.sd.server.api.BadRequestException
import ar.edu.austral.inf.sd.server.api.NotFoundException
import ar.edu.austral.inf.sd.server.api.ReconfigureApiService
import ar.edu.austral.inf.sd.server.api.UnregisterNodeApiService
import ar.edu.austral.inf.sd.server.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.postForEntity
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.naming.ServiceUnavailableException
import kotlin.random.Random
import kotlin.system.exitProcess

@Component
class ApiServicesImpl : RegisterNodeApiService, RelayApiService, PlayApiService, UnregisterNodeApiService, ReconfigureApiService {

    @Value("\${server.name:nada}")
    private val myServerName: String = ""

    @Value("\${server.port:8080}")
    private val myServerPort: Int = 0

    @Value("\${server.timeout:20}")
    private val timeout: Int = 20

    @Value("\${register.host:}")
    var registerHost: String = ""

    @Value("\${register.port:-1}")
    var registerPort: Int = -1

    private var timeOuts = 0
    private val nodes: MutableList<Node> = mutableListOf()
    private var nextNode: RegisterResponse? = null
    private val messageDigest = MessageDigest.getInstance("SHA-512")
    private val salt = Base64.getEncoder().encodeToString(Random.nextBytes(9))
    private val currentRequest
        get() = (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes).request
    private var resultReady = CountDownLatch(1)
    private var currentMessageWaiting = MutableStateFlow<PlayResponse?>(null)
    private var currentMessageResponse = MutableStateFlow<PlayResponse?>(null)
    private var xGameTimestamp: Int = 0
    private val uuid = UUID.randomUUID()
    private val restTemplate = RestTemplate()

    override fun registerNode(host: String?, port: Int?, uuid: UUID?, salt: String?, name: String?): ResponseEntity<RegisterResponse> {
        if (host.isNullOrEmpty() || port == null || uuid == null || salt.isNullOrEmpty() || name.isNullOrEmpty()) {
            throw BadRequestException("Invalid input parameters")
        }

        val existingNode = nodes.find { it.uuid == uuid }
        if (existingNode != null) {
            return if (existingNode.salt == salt) {
                ResponseEntity(RegisterResponse(existingNode.nextHost, existingNode.nextPort, existingNode.timeout, existingNode.xGameTimestamp), HttpStatus.ACCEPTED)
            } else {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null)
            }
        }

        val nextNode = if (nodes.isEmpty()) {
            val firstNode = Node(currentRequest.serverName, myServerPort, timeout, xGameTimestamp, this.salt, this.uuid)
            nodes.add(firstNode)
            firstNode
        } else {
            nodes.last()
        }
        val node = Node(host, port, timeout, xGameTimestamp, salt, uuid)
        nodes.add(node)

        val response = RegisterResponse(nextNode.nextHost, nextNode.nextPort, timeout, xGameTimestamp)
        return ResponseEntity(response, HttpStatus.CREATED)
    }

    override fun relayMessage(message: String, signatures: Signatures, xGameTimestamp: Int?): Signature {
        val receivedHash = doHash(message.encodeToByteArray(), salt)
        val receivedContentType = currentRequest.getPart("message")?.contentType ?: "nada"
        val receivedLength = message.length
        if (nextNode != null) {
            val updatedSignatures = signatures.items + clientSign(message, receivedContentType)
            sendRelayMessage(message, receivedContentType, nextNode!!, Signatures(updatedSignatures), xGameTimestamp!!)
        } else {
            if (currentMessageWaiting.value == null) throw BadRequestException("no waiting message")
            val current = currentMessageWaiting.getAndUpdate { null }!!
            val response = validatePlayResult(current, receivedHash, receivedLength, receivedContentType, signatures)
            currentMessageResponse.update { response }
            resultReady.countDown()
        }
        return Signature(
            name = myServerName,
            hash = receivedHash,
            contentType = receivedContentType,
            contentLength = receivedLength
        )
    }

    override fun sendMessage(body: String): PlayResponse {
        if (nodes.isEmpty()) {
            val firstNode = Node(currentRequest.serverName, myServerPort, timeout, xGameTimestamp, this.salt, UUID.randomUUID())
            nodes.add(firstNode)
        }
        currentMessageWaiting.update { newResponse(body) }
        val contentType = currentRequest.contentType
        sendRelayMessage(body, contentType, nodeToRegisterResponse(nodes.last()), Signatures(listOf()), xGameTimestamp)
        resultReady.await()
        resultReady = CountDownLatch(1)
        return currentMessageResponse.value!!
    }

    override fun unregisterNode(uuid: UUID?, salt: String?): String {
        val unregisterNode = nodes.find { it.uuid == uuid }

        if (unregisterNode == null) {
            throw NotFoundException("Node not found")
        }

        if (unregisterNode.salt != salt) {
            throw BadRequestException("Invalid salt")
        }
        val index = nodes.indexOf(unregisterNode)

        if (index < nodes.size - 1) {
            val nextNode = nodes[index - 1]
            val previousNode = nodes[index + 1]

            val url = "http://${previousNode.nextHost}:${previousNode.nextPort}/reconfigure"
            val params = "?uuid=${nextNode.uuid}&salt=${nextNode.salt}&nextHost=${nextNode.nextHost}&nextPort=${nextNode.nextPort}"
            val fullUrl = url + params
            val headers = HttpHeaders().apply {
                add("X-Game-Timestamp", xGameTimestamp.toString())
            }
            val request = HttpEntity(null, headers)

            try {
                restTemplate.postForEntity<String>(fullUrl, request)
            } catch (e: Exception) {
                println("Error occurred while reconfiguring the node: ${e.message}")
                throw e
            }
        }
        nodes.removeAt(index)
        return "Node unregistered"
    }

    override fun reconfigure(uuid: UUID?, salt: String?, nextHost: String?, nextPort: Int?, xGameTimestamp: Int?): String {
        val reconfigureNode = nodes.find { it.uuid == uuid }

        if (reconfigureNode == null) {
            throw NotFoundException("Node not found")
        }

        if (reconfigureNode.salt != salt) {
            throw BadRequestException("Invalid salt")
        }

        reconfigureNode.nextHost = nextHost!!
        reconfigureNode.nextPort = nextPort!!
        reconfigureNode.xGameTimestamp = xGameTimestamp!!

        return "Node reconfigured"
    }

    internal fun registerToServer(registerHost: String, registerPort: Int) {
        println("My salt: $salt")
        val registerUrl = "http://$registerHost:$registerPort/register-node"
        val registerParams = "?host=localhost&port=$myServerPort&name=$myServerName&uuid=$uuid&salt=$salt&name=$myServerName"
        val url = registerUrl + registerParams

        try {
            val response = restTemplate.postForEntity<RegisterResponse>(url)
            val registerNodeResponse: RegisterResponse = response.body!!
            println("nextNode = $registerNodeResponse")
            xGameTimestamp = registerNodeResponse.xGameTimestamp
            nextNode = with(registerNodeResponse) { RegisterResponse(nextHost, nextPort, timeout, xGameTimestamp) }
        } catch (e: RestClientException) {
            println("Could not register to: $registerUrl")
            println("Params: $registerParams")
            println("Error: ${e.message}")
            println("Shutting down")
            exitProcess(1)
        }
    }

    private fun sendRelayMessage(body: String, contentType: String, relayNode: RegisterResponse, signatures: Signatures, timestamp: Int) {
        if (timestamp < xGameTimestamp) {
            throw BadRequestException("Invalid timestamp")
        }

        val nextNodeUrl = "http://${relayNode.nextHost}:${relayNode.nextPort}/relay"
        val messageHeaders = HttpHeaders().apply { setContentType(MediaType.parseMediaType(contentType)) }
        val messagePart = HttpEntity(body, messageHeaders)

        val signatureHeaders = HttpHeaders().apply { setContentType(MediaType.APPLICATION_JSON) }
        val signaturesPart = HttpEntity(signatures, signatureHeaders)

        val bodyParts = LinkedMultiValueMap<String, Any>().apply {
            add("message", messagePart)
            add("signatures", signaturesPart)
        }

        val requestHeaders = HttpHeaders().apply {
            setContentType(MediaType.MULTIPART_FORM_DATA)
            add("X-Game-Timestamp", timestamp.toString())
        }
        val request = HttpEntity(bodyParts, requestHeaders)

        try {
            restTemplate.postForEntity<Map<String, Any>>(nextNodeUrl, request)
        } catch (e: RestClientException) {
            val hostUrl = "http://${registerHost}:${registerPort}/relay"
            restTemplate.postForEntity<Map<String, Any>>(hostUrl, request)
            throw ServiceUnavailableException("Could not relay message to: $nextNodeUrl")
        }

        xGameTimestamp = timestamp
    }

    private fun clientSign(message: String, contentType: String): Signature {
        val receivedHash = doHash(message.encodeToByteArray(), salt)
        return Signature(myServerName, receivedHash, contentType, message.length)
    }

    private fun newResponse(body: String) = PlayResponse(
        "Unknown",
        currentRequest.contentType,
        body.length,
        doHash(body.encodeToByteArray(), salt),
        "Unknown",
        -1,
        "N/A",
        Signatures(listOf())
    )

    private fun doHash(body: ByteArray, salt: String): String {
        val saltBytes = Base64.getDecoder().decode(salt)
        messageDigest.update(saltBytes)
        val digest = messageDigest.digest(body)
        return Base64.getEncoder().encodeToString(digest)
    }

    private fun nodeToRegisterResponse(node: Node): RegisterResponse {
        return RegisterResponse(
            nextHost = node.nextHost,
            nextPort = node.nextPort,
            timeout = node.timeout,
            xGameTimestamp = node.xGameTimestamp
        )
    }

    private fun validatePlayResult(current: PlayResponse, receivedHash: String, receivedLength: Int, receivedContentType: String, signatures: Signatures): PlayResponse {
        return current.copy(
            contentResult = if (receivedHash == current.originalHash) "Success" else "Failure",
            receivedHash = receivedHash,
            receivedLength = receivedLength,
            receivedContentType = receivedContentType,
            signatures = signatures
        )
    }

    companion object {
        fun newSalt(): String = Base64.getEncoder().encodeToString(Random.nextBytes(9))
    }
}