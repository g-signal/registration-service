package org.signal.registration.sender.aliyun;

import okhttp3.*;
import com.google.gson.Gson;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AliyunSmsOkHttpClient {
    private static final String ENDPOINT = "https://dysmsapi.aliyuncs.com";
    private static final String REGION = "cn-hangzhou";
    private final OkHttpClient httpClient;
    private final String accessKeyId;
    private final String accessKeySecret;

    public AliyunSmsOkHttpClient(String accessKeyId, String accessKeySecret) {
        this.httpClient = new OkHttpClient();
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
    }

    public String sendSms(String phoneNumber, String signName, String templateCode, String templateParam) throws Exception {
        Map<String, String> params = new TreeMap<>();
        params.put("Action", "SendSms");
        params.put("Version", "2017-05-25");
        params.put("RegionId", REGION);
        params.put("PhoneNumbers", phoneNumber);
        params.put("SignName", signName);
        params.put("TemplateCode", templateCode);
        params.put("TemplateParam", templateParam);

        params.put("Format", "JSON");
        params.put("AccessKeyId", accessKeyId);
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("Timestamp", ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT));
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", UUID.randomUUID().toString());

        String signature = calculateSignature(params, "POST");
        params.put("Signature", signature);

        FormBody.Builder formBuilder = new FormBody.Builder();
        params.forEach(formBuilder::add);

        Request request = new Request.Builder()
                .url(ENDPOINT)
                .post(formBuilder.build())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Request failed: " + response.code());
            }

            String responseBody = response.body().string();
            Map<String, Object> result = new Gson().fromJson(responseBody, Map.class);

            if ("OK".equals(result.get("Code"))) {
                return (String) result.get("BizId");
            }
            return null;
        }
    }

    private String calculateSignature(Map<String, String> params, String method) throws Exception {
        StringBuilder canonicalizedQueryString = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!entry.getKey().equals("Signature")) {
                canonicalizedQueryString.append("&")
                        .append(percentEncode(entry.getKey()))
                        .append("=")
                        .append(percentEncode(entry.getValue()));
            }
        }
        String stringToSign = method + "&" + percentEncode("/") + "&" +
                percentEncode(canonicalizedQueryString.substring(1));

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec((accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signData);
    }

    private String percentEncode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}
