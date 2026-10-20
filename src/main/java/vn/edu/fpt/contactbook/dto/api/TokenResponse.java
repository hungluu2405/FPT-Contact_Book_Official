package vn.edu.fpt.contactbook.dto.api;

/** expiresIn: số giây access token còn hiệu lực. */
public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
                            MeResponse user) {

    public static final String BEARER = "Bearer";
}
