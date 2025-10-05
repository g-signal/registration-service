package org.signal.registration.sender.aliyun;


import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AliyunSmsUtil {

  private static final Logger logger = LoggerFactory.getLogger(AliyunSmsUtil.class);

  public static SendSmsResponse send(String regionId, String accessKeyId, String accessSecret, String templateCode,
      String signName, String phoneNumber, String code) throws ClientException {
    JSONObject dataJson = new JSONObject();
    dataJson.put("code", code);

    return sendSms2(regionId, accessKeyId, accessSecret, templateCode, signName, phoneNumber, dataJson);
  }

  public static SendSmsResponse sendSms2(String regionId, String accessKeyId, String accessSecret, String templateCode,
      String signName, String phoneNumber, JSONObject dataJson) throws ClientException {

    DefaultProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessSecret);

    IAcsClient client = new DefaultAcsClient(profile);

    // 创建API请求并设置参数
    SendSmsRequest request = new SendSmsRequest();

    request.setPhoneNumbers(phoneNumber); // 该参数值为假设值，请您根据实际情况进行填写

    request.setSignName(signName); // 该参数值为假设值，请您根据实际情况进行填写

    request.setTemplateCode(templateCode);

    request.setTemplateParam(dataJson.toString());
    request.setEndpoint("dysmsapi.aliyuncs.com");
    SendSmsResponse response = client.getAcsResponse(request);
    return response;
  }
}
