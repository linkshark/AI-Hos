package com.linkjb.aimed.service;

import com.linkjb.aimed.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MailSenderService {

    private static final Logger log = LoggerFactory.getLogger(MailSenderService.class);

    private final JavaMailSender javaMailSender;
    private final AuthProperties authProperties;
    private final String mailFrom;
    private final String mailUsername;
    private final String mailPassword;
    public MailSenderService(JavaMailSender javaMailSender,
                             AuthProperties authProperties,
                             @Value("${spring.mail.username:}") String mailUsername,
                             @Value("${spring.mail.password:}") String mailPassword,
                             @Value("${MAIL_FROM:}") String mailFrom) {
        this.javaMailSender = javaMailSender;
        this.authProperties = authProperties;
        this.mailFrom = mailFrom;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
    }

    public void sendRegisterCode(String to, String code) {
        sendVerificationMail(
                to,
                "杭州树兰医院智能服务平台注册验证码",
                "您好，您的注册验证码为：" + code + "。\n\n验证码将在 10 分钟后失效，请勿泄露给他人。"
        );
    }

    public void sendAppointSuccess(String email,String message) {
        log.info("appointment.mail.send to={}", email);
        sendVerificationMail(
                email,
                "杭州树兰医院智能服务平台预约成功通知",
                "您好，您的预约信息已确认：" + "\n\n"+message
        );
    }

    public void sendPasswordResetCode(String to, String code) {
        sendVerificationMail(
                to,
                "杭州树兰医院智能服务平台密码重置验证码",
                "您好，您的密码重置验证码为：" + code + "。\n\n验证码将在 10 分钟后失效。如非本人操作，请忽略本邮件。"
        );
    }

    private void sendVerificationMail(String to, String subject, String text) {
        if (authProperties.isMailMockEnabled()) {
            log.info("auth.mail.mock to={} subject={}", to, subject);
            return;
        }
        if (!StringUtils.hasText(mailUsername) || !StringUtils.hasText(mailPassword)) {
            throw new IllegalStateException("邮件服务未配置，无法发送验证码");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setFrom(StringUtils.hasText(mailFrom) ? mailFrom : mailUsername);
        message.setSubject(subject);
        message.setText(text);
        try {
            javaMailSender.send(message);
        } catch (MailException exception) {
            log.error("auth.mail.send.failed to={}", to, exception);
            throw new IllegalStateException("验证码发送失败，请检查邮箱 SMTP 配置或网络连通性");
        }
    }
}
