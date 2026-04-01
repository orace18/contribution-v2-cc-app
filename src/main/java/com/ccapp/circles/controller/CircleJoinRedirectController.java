package com.ccapp.circles.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CircleJoinRedirectController {

    @Value("${circles.invite.deep-link-base:ccapp://circles/join}")
    private String deepLinkBase;

    @Value("${app.store.android:https://play.google.com/store/apps/details?id=com.ccapp}")
    private String playStoreUrl;

    @Value("${app.store.ios:https://apps.apple.com/app/id000000000}")
    private String appStoreUrl;

    @GetMapping(value = "/join", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> join(@RequestParam String token) {
        String deepLink = deepLinkBase + "?token=" + token;
        String html = """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                    <meta charset="UTF-8"/>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                    <title>Rejoindre un cercle</title>
                    <style>
                        body {
                            font-family: sans-serif;
                            display: flex;
                            flex-direction: column;
                            align-items: center;
                            justify-content: center;
                            min-height: 100vh;
                            margin: 0;
                            background: #f5f5f5;
                            color: #333;
                        }
                        .card {
                            background: white;
                            border-radius: 16px;
                            padding: 40px 32px;
                            text-align: center;
                            box-shadow: 0 2px 16px rgba(0,0,0,0.08);
                            max-width: 360px;
                            width: 90%%;
                        }
                        .logo { font-size: 48px; margin-bottom: 16px; }
                        h1 { font-size: 20px; margin: 0 0 8px; color: #109F76; }
                        p { font-size: 14px; color: #666; margin: 0 0 24px; }
                        .btn {
                            display: inline-block;
                            padding: 12px 28px;
                            border-radius: 8px;
                            background: #109F76;
                            color: white;
                            text-decoration: none;
                            font-size: 15px;
                            font-weight: 600;
                        }
                        .store-links { margin-top: 20px; font-size: 13px; color: #999; }
                        .store-links a { color: #109F76; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="logo">🤝</div>
                        <h1>Rejoindre un cercle</h1>
                        <p id="msg">Ouverture de l'application...</p>
                        <a class="btn" href="%s">Ouvrir l'app</a>
                        <div class="store-links" id="store-links" style="display:none">
                            <p>Vous n'avez pas l'application ?</p>
                            <a id="store-link" href="#">Télécharger</a>
                        </div>
                    </div>
                    <script>
                        const deepLink = "%s";
                        const playStore = "%s";
                        const appStore = "%s";
                        const isAndroid = /Android/i.test(navigator.userAgent);
                        const isIOS = /iPhone|iPad|iPod/i.test(navigator.userAgent);

                        window.location = deepLink;

                        setTimeout(function () {
                            document.getElementById("msg").textContent = "L'application ne s'est pas ouverte.";
                            const storeLinks = document.getElementById("store-links");
                            const storeLink = document.getElementById("store-link");
                            if (isAndroid) {
                                storeLink.href = playStore;
                                storeLink.textContent = "Télécharger sur Google Play";
                                storeLinks.style.display = "block";
                            } else if (isIOS) {
                                storeLink.href = appStore;
                                storeLink.textContent = "Télécharger sur l'App Store";
                                storeLinks.style.display = "block";
                            } else {
                                storeLinks.style.display = "block";
                                storeLink.textContent = "Disponible sur Android et iOS";
                                storeLink.href = "#";
                            }
                        }, 2500);
                    </script>
                </body>
                </html>
                """.formatted(deepLink, deepLink, playStoreUrl, appStoreUrl);

        return ResponseEntity.ok(html);
    }
}
