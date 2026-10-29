package uk.adedamola.stargazer.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/** The grant type of GitHub's device-code sign-in (OAuth device authorization grant). */
const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"

/**
 * GitHub's device-code sign-in endpoints on github.com. They take the public client ID only:
 * no client secret exists, and no request carries an access key.
 */
interface DeviceFlowApi {

    @FormUrlEncoded
    @POST("login/device/code")
    suspend fun requestCode(@Field("client_id") clientId: String): DeviceCodeResponse

    /** GitHub answers HTTP 200 with either `access_token` or `error`. */
    @FormUrlEncoded
    @POST("login/oauth/access_token")
    suspend fun pollToken(
        @Field("client_id") clientId: String,
        @Field("device_code") deviceCode: String,
        @Field("grant_type") grantType: String = DEVICE_GRANT
    ): TokenPollResponse
}

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = 5
) {
    // The device code lets anyone finish this sign-in; keep it out of logs and crash reports.
    override fun toString(): String =
        "DeviceCodeResponse(userCode=$userCode, verificationUri=$verificationUri, expiresIn=$expiresIn, interval=$interval)"
}

@Serializable
data class TokenPollResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val interval: Int? = null
) {
    override fun toString(): String =
        "TokenPollResponse(accessToken=${if (accessToken == null) "null" else "██"}, error=$error, interval=$interval)"
}
