package org.signal.registration.sender.aliyun;


/**
 * @Author Jinyang
 * @Date 6/20/24
 */
public class AliyunSmsUtil {

    private static final String REGION_ID = "cn-hangzhou";

    /**
     * 其它国家码使用的通用膜拜
     */
    private static final String OTHER_KEY = "other";

    public static String sendCode(String accessKeyId,
        String accessSecret,
        String templateCode,
        String templateParamKey,
        String signName,
        String phoneNumber,
        String templateParam) {

      AliyunSmsOkHttpClient client = new AliyunSmsOkHttpClient(accessKeyId,
          accessSecret);
      String bizId = null;
      try {
        bizId = client.sendSms(
            phoneNumber,
            signName,
            templateCode,
            "{\""+templateParamKey+"\":\""+templateParam+"\"}"
        );
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

      return bizId;
    }
}
