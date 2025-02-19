package org.book.commerce.userservice.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.book.commerce.common.entity.ErrorCode;
import org.book.commerce.common.exception.CommonException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender emailSender;
    private Session session;

    public boolean sendEmail(String toEmail,String title, String key){
        try{
            MimeMessage emailForm = createEmailForm(toEmail,title,key);
            emailSender.send(emailForm);
        } catch (Exception e){
            throw new CommonException("메일이 올바르지않습니다. 다시 확인해주세요.", ErrorCode.INVALID_EMAIL);
        }
        return true;
    }

    private String getApiGatewayUrl() {
        return System.getenv("API_GATEWAY_URL");
    }
    private MimeMessage createEmailForm(String toEmail,String title,String key) throws MessagingException {
        String apiGatewayUrl = getApiGatewayUrl();
        MimeMessage message = new MimeMessage(session);
        message.addRecipients(Message.RecipientType.TO,toEmail);
        message.setSubject(title);
        message.setContent("<h1>[이메일 인증]</h1> <p>아래 링크를 클릭하시면 이메일 인증이 완료됩니다.</p> " +
                "<a href='" + apiGatewayUrl + "/auth/signup/email-verifications?key=" + key + "' target='_blenk'>이메일 인증 확인</a>", "text/html;charset=euc-kr"
        );
        return message;
    }
}



