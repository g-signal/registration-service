package org.signal.registration.sender.aliyun;


import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.QuerySmsTemplateRequest;
import com.aliyuncs.dysmsapi.model.v20170525.QuerySmsTemplateResponse;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import org.apache.commons.lang3.StringUtils;

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

        DefaultProfile profile = DefaultProfile.getProfile(REGION_ID, accessKeyId, accessSecret);
        profile.getHttpClientConfig().setProtocolType(ProtocolType.HTTPS);
        IAcsClient client = new DefaultAcsClient(profile);

        // 创建API请求并设置参数
        SendSmsRequest request = new SendSmsRequest();

        // 该参数值为假设值，请您根据实际情况进行填写
        request.setPhoneNumbers(phoneNumber);

        // 该参数值为假设值，请您根据实际情况进行填写
        request.setSignName(signName);

        request.setTemplateCode(templateCode);

        request.setTemplateParam("{\""+templateParamKey+"\":\""+templateParam+"\"}");
        request.setEndpoint("dysmsapi.aliyuncs.com");
        try {
            SendSmsResponse response = client.getAcsResponse(request);
            if (response.getCode() != null && "OK".equals(response.getCode())) {
                return response.getBizId();
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
