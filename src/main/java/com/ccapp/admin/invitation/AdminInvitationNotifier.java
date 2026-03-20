package com.ccapp.admin.invitation;

import com.ccapp.admin.domain.entity.AdminInvitation;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
@Slf4j
@RequiredArgsConstructor
public class AdminInvitationNotifier {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${admin.invitation.base-url:https://backoffice.platform.com/activate}")
    private String baseUrl;

    @Async
    public void sendInvitation(AdminInvitation invitation) {
        String activationLink = baseUrl + "?token=" + invitation.getInvitationToken();
        String roleName = formatRole(invitation.getRole().name());
        String expiresAt = invitation.getExpiresAt()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm"));

        String subject = "Invitation Administrateur — Club Chrétien";
        String body = buildInvitationEmailBody(invitation.getEmail(), roleName, activationLink, expiresAt);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(invitation.getEmail());
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("Invitation email sent to {} role={}", invitation.getEmail(), invitation.getRole());
        } catch (MessagingException e) {
            log.error("Failed to send invitation email to {} : {}", invitation.getEmail(), e.getMessage(), e);
        }
    }

    private String buildInvitationEmailBody(String email, String role, String activationLink, String expiresAt) {
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>Invitation Administrateur</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f8;padding:40px 0;">
                    <tr>
                      <td align="center">
                        <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                          <!-- Header -->
                          <tr>
                            <td style="background:#109F76;padding:32px 40px;text-align:center;">
                              <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;letter-spacing:0.5px;">
                                Club Chrétien — Backoffice
                              </h1>
                            </td>
                          </tr>

                          <!-- Body -->
                          <tr>
                            <td style="padding:40px;">
                              <p style="margin:0 0 16px;color:#1a1a1a;font-size:15px;">Bonjour,</p>
                              <p style="margin:0 0 16px;color:#444;font-size:15px;line-height:1.6;">
                                Vous avez été invité(e) à rejoindre le backoffice administrateur de
                                <strong>Club Chrétien</strong> avec le rôle <strong>%s</strong>.
                              </p>
                              <p style="margin:0 0 28px;color:#444;font-size:15px;line-height:1.6;">
                                Cliquez sur le bouton ci-dessous pour activer votre compte et définir votre mot de passe.
                              </p>

                              <!-- CTA Button -->
                              <table cellpadding="0" cellspacing="0" style="margin:0 auto 28px;">
                                <tr>
                                  <td style="background:#109F76;border-radius:6px;">
                                    <a href="%s"
                                       style="display:inline-block;padding:14px 32px;color:#ffffff;font-size:15px;font-weight:600;text-decoration:none;letter-spacing:0.3px;">
                                      Activer mon compte
                                    </a>
                                  </td>
                                </tr>
                              </table>

                              <!-- Fallback link -->
                              <p style="margin:0 0 8px;color:#888;font-size:12px;">Si le bouton ne fonctionne pas, copiez ce lien dans votre navigateur :</p>
                              <p style="margin:0 0 28px;word-break:break-all;">
                                <a href="%s" style="color:#109F76;font-size:12px;">%s</a>
                              </p>

                              <!-- Warning -->
                              <table width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td style="background:#FFF8E1;border-left:4px solid #FFAA15;padding:12px 16px;border-radius:4px;">
                                    <p style="margin:0;color:#7a5c00;font-size:13px;">
                                      Ce lien expire le <strong>%s</strong>. Passé ce délai, une nouvelle invitation devra être envoyée.
                                    </p>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="background:#f9f9f9;padding:20px 40px;border-top:1px solid #eeeeee;text-align:center;">
                              <p style="margin:0;color:#aaa;font-size:12px;">
                                Cet email a été envoyé à %s. Si vous n'êtes pas concerné(e), ignorez ce message.
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(role, activationLink, activationLink, activationLink, expiresAt, email);
    }

    private String formatRole(String roleName) {
        return switch (roleName) {
            case "ROLE_SUPER_ADMIN" -> "Super Administrateur";
            case "ROLE_COUNTRY_ADMIN" -> "Administrateur Pays";
            case "ROLE_OPERATIONS" -> "Opérations";
            case "ROLE_SUPPORT" -> "Support";
            case "ROLE_IMF_ADMIN" -> "Administrateur IMF";
            default -> roleName;
        };
    }
}
