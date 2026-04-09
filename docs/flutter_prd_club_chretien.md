# Flutter Product Requirement Document - Club Chretien

Date: 2026-04-09

Source of truth:
- `docs/mobile_api_contract_v1.md`
- backend canonique deja implemente dans `cc-backend-monolitic`

Contraintes produit non negociables:
- ne pas recreer de logique legacy de `caisse du cercle`
- ne pas parler de `tontine` ou de `cotisation legacy` dans l'UX
- ne pas construire de module `projet` separe de `CreditApplication`
- ne pas introduire de live meeting, appel audio/video ou orchestration RTC
- ne pas utiliser les endpoints de creation directe de pret comme entree seed capital mobile

## 1. Vision produit

Club Chretien doit devenir l'application mobile de reference pour l'entraide economique communautaire chretienne.

Le produit doit fonctionner comme un `WhatsApp de la finance communautaire`:
- conversation simple et rassurante
- actions visibles
- progression claire
- discipline communautaire lisible
- financement base sur la confiance et la regularite

Le cercle est l'unite de base du produit:
- un cercle contient de `3 a 6 membres`
- les membres prient, se soutiennent, se reunissent et avancent ensemble
- la valeur du cercle vient de la constance communautaire, pas d'une caisse commune

La logique seed capital est une logique de progression:
- le financement est prepare par la discipline du cercle
- la readiness depend des reunions, du trust score, du soutien reseau, de l'epargne personnelle et de l'etat IMF
- la demande finale passe uniquement par `CreditApplication`

Le soutien communautaire doit etre visible partout dans l'application:
- voir les membres
- prier pour eux
- les encourager
- voir si le cercle avance
- sentir qu'on ne finance pas un dossier mais une communaute responsable

## 2. Personas

| Persona | Situation | Objectifs principaux | Freins principaux | Ce que l'application doit optimiser |
| --- | --- | --- | --- | --- |
| Membre de cercle | Utilisateur peu digitalise, utilise surtout WhatsApp et mobile money | activer son compte, rejoindre son cercle, suivre les actions a faire, contribuer au cycle, comprendre s'il devient eligible au seed capital | peur des interfaces compliquees, vocabulaire financier, erreurs de paiement | parcours courts, gros boutons, etats tres clairs, messages motivants |
| Leader de cercle | Membre responsable de la discipline du groupe | suivre les membres, tenir une reunion, cadrer la frequence des reunions, ouvrir un cycle d'epargne, relancer les membres | manque de temps, besoin d'une vision rapide du cercle | ecran cercle tres lisible, CTA de gestion visibles, vues synthese |
| Coach reseau | Acteur d'accompagnement qui aide un ou plusieurs cercles | suivre le trust score, l'eligibilite, la presence d'un coach, les signaux de blocage et la progression vers le seed capital | trop d'ecrans disperses, besoin d'aller vite | vues readiness et conversations accessibles en peu de taps |

## 3. Navigation globale

### 3.1 Architecture recommandee

1. `Auth / Onboarding stack`
- creation de compte / connexion
- verification telephone
- selection du pays
- selection du plan membership
- activation membership
- reprise du statut onboarding

2. `Main shell`
- onglet `Accueil`
- onglet `Cercles`
- onglet `Chat`
- onglet `Financement`
- onglet `Profil`

3. `Secondary flows`
- notifications
- recherche
- detail cercle
- detail membre
- meetings
- epargne / contribution
- detail demande seed capital
- wallet

### 3.2 Regles de navigation

- `Accueil` est le point d'entree principal apres connexion.
- `Cercles` est l'entree principale vers la vie communautaire.
- `Chat` agrege les conversations directes et les conversations de cercle.
- `Financement` regroupe l'eligibilite, la demande seed capital, les prets et les remboursements.
- `Profil` regroupe le financial profile, le statut membership, l'etat wallet et l'etat IMF.
- les notifications ouvrent des deep links vers le bon ecran fonctionnel
- la recherche doit etre accessible depuis l'entete de `Cercles`

### 3.3 Regles de composition mobile

- utiliser `GET /api/mobile/home` quand l'agregat est utile pour l'ecran d'accueil
- completer avec les endpoints du contrat mobile pour les ecrans detail
- plusieurs ecrans doivent etre composes cote Flutter
- la couche reseau Flutter doit normaliser deux enveloppes:
  - `CommonResponse`
  - `ApiEnvelope`

## 4. Liste complete des ecrans

### 4.1 Ecran Creation de compte / Connexion

**Objectif**

Permettre a l'utilisateur d'entrer dans le produit avec un parcours tres court.

**Donnees necessaires**

- fournisseur de connexion choisi
- statut de session
- premier profil utilisateur mobile

**API utilisees**

- `POST /api/mobile/auth/social-login`

**Etats UI**

- chargement
- succes de connexion
- compte cree
- echec de connexion

**Actions utilisateur**

- se connecter
- reessayer

**CTA**

- `Continuer`
- `Reessayer`

**Erreurs possibles**

- token fournisseur invalide
- reseau indisponible
- session non creee

### 4.2 Ecran Verification telephone

**Objectif**

Confirmer le numero de telephone avant les parcours wallet et membership.

**Donnees necessaires**

- numero de telephone
- verificationId
- canal OTP ou Firebase
- etat de verification

**API utilisees**

- `POST /api/mobile/phone-verifications/start`
- `POST /api/mobile/phone-verifications/{verificationId}/confirm`
- `POST /api/mobile/phone-verifications/{verificationId}/resend`
- `POST /api/mobile/phone-verifications/firebase`
- `GET /api/mobile/phone-verifications/{verificationId}`

**Etats UI**

- numero non verifie
- code envoye
- code expire
- verification confirmee

**Actions utilisateur**

- saisir numero
- demander le code
- saisir OTP
- renvoyer le code

**CTA**

- `Envoyer le code`
- `Valider mon numero`
- `Renvoyer le code`

**Erreurs possibles**

- code OTP invalide
- code expire
- probleme reseau

### 4.3 Ecran Selection pays et plan membership

**Objectif**

Choisir le contexte pays et le plan d'entree dans le produit.

**Donnees necessaires**

- pays selectionne
- liste des plans disponibles
- plan choisi
- statut onboarding

**API utilisees**

- `POST /api/mobile/onboarding/select-country`
- `GET /api/mobile/onboarding/plans?country=BJ`
- `POST /api/mobile/onboarding/select-plan`
- `GET /api/mobile/onboarding/status`

**Etats UI**

- aucun pays choisi
- chargement du catalogue
- plan gratuit
- plan payant
- plan selectionne

**Actions utilisateur**

- choisir un pays
- comparer les plans
- selectionner un plan

**CTA**

- `Choisir ce pays`
- `Choisir ce plan`
- `Continuer`

**Erreurs possibles**

- pays non supporte
- plan introuvable
- catalogue indisponible

### 4.4 Ecran Activation membership

**Objectif**

Finaliser l'entree dans le produit par activation gratuite ou paiement wallet.

**Donnees necessaires**

- plan selectionne
- etat wallet
- solde wallet
- pin configure ou non
- resultat du paiement
- financial profile apres activation

**API utilisees**

- `POST /api/mobile/onboarding/activate-free`
- `POST /subscriptions/initiate`
- `GET /wallet/status`
- `GET /wallet/balance?currency=XOF`
- `GET /me/financial-profile`
- `GET /api/mobile/onboarding/status`

**Etats UI**

- plan gratuit activable
- plan payant activable
- wallet inactif
- pin wallet manquant
- OTP requis
- activation reussie

**Actions utilisateur**

- activer gratuitement
- payer depuis le wallet
- saisir le pin
- saisir le code OTP si necessaire

**CTA**

- `Activer gratuitement`
- `Payer mon membership`
- `Valider le code`

**Erreurs possibles**

- solde insuffisant
- pin incorrect
- OTP invalide ou expire
- wallet inactif

### 4.5 Ecran Accueil / Dashboard

**Objectif**

Donner en une vue la prochaine action, les alertes importantes et l'entree rapide vers cercle, contribution et financement.

**Donnees necessaires**

- resume home mobile
- unread notifications count
- cercle actif
- statut membership
- financial profile

**API utilisees**

- `GET /api/mobile/home`
- `GET /notifications/me/unread-count`
- `GET /circles/my-circle`
- `GET /me/financial-profile`

**Etats UI**

- premier chargement
- utilisateur sans cercle
- utilisateur avec cercle actif
- onboarding encore incomplet
- membership inactif ou expirant
- prochaine action disponible

**Actions utilisateur**

- ouvrir son cercle
- ouvrir les notifications
- reprendre une action
- aller vers le financement

**CTA**

- `Voir mon cercle`
- `Faire ma prochaine action`
- `Voir mes notifications`

**Erreurs possibles**

- dashboard indisponible
- session expiree
- erreur reseau

### 4.6 Ecran Notifications

**Objectif**

Afficher les notifications systeme et permettre la lecture rapide.

**Donnees necessaires**

- liste des notifications
- compteur non lues
- deep links cibles

**API utilisees**

- `GET /notifications/me`
- `GET /notifications/me/unread-count`
- `PATCH /notifications/me/{notificationId}/read`
- `PATCH /notifications/me/read-all`

**Etats UI**

- aucune notification
- notifications non lues
- notifications toutes lues

**Actions utilisateur**

- ouvrir une notification
- marquer une notification lue
- tout marquer comme lu

**CTA**

- `Ouvrir`
- `Marquer comme lu`
- `Tout marquer comme lu`

**Erreurs possibles**

- notification introuvable
- erreur reseau

### 4.7 Ecran Liste des cercles

**Objectif**

Afficher les cercles de l'utilisateur de maniere chat-first.

**Donnees necessaires**

- liste des cercles
- nom
- statut
- progression
- nombre de membres
- role
- unreadMessages
- lastActivity

**API utilisees**

- `GET /circles?page=0&size=20`
- `GET /notifications/me/unread-count`

**Etats UI**

- aucun cercle
- un seul cercle
- plusieurs cercles
- chargement

**Actions utilisateur**

- ouvrir un cercle
- rechercher
- creer un cercle

**CTA**

- `Ouvrir`
- `Rechercher`
- `Creer un cercle`

**Erreurs possibles**

- liste indisponible
- session expiree

### 4.8 Ecran Recherche

**Objectif**

Permettre une recherche unifiee de cercles et de membres.

**Donnees necessaires**

- texte de recherche
- resultats cercles
- resultats membres

**API utilisees**

- `GET /directory/search?q=&scopes=circles,members`

**Etats UI**

- aucun texte saisi
- recherche en cours
- resultat vide
- resultats mixtes

**Actions utilisateur**

- saisir une requete
- ouvrir un cercle
- ouvrir un membre

**CTA**

- `Rechercher`
- `Voir le cercle`
- `Voir le profil`

**Erreurs possibles**

- scope invalide
- erreur reseau

### 4.9 Ecran Detail cercle

**Objectif**

Donner la vue canonique d'un cercle et servir de hub vers chat, meetings, membres, epargne et progression.

**Donnees necessaires**

- detail canonique du cercle
- participants
- activite recente
- coach assignment si disponible

**API utilisees**

- `GET /circles/{circleId}`
- `GET /circles/{circleId}/participants`
- `GET /circles/{circleId}/activity`
- `GET /circles/{circleId}/coach-assignment`

**Etats UI**

- cercle actif
- cercle incomplet
- cercle sans coach
- cercle accessible en lecture seule

**Actions utilisateur**

- ouvrir le chat
- voir les membres
- voir les meetings
- voir l'epargne
- voir la progression
- ouvrir la configuration si role autorise

**CTA**

- `Ouvrir le chat`
- `Voir les membres`
- `Voir les reunions`
- `Voir l'epargne`
- `Voir la progression`

**Erreurs possibles**

- cercle introuvable
- acces refuse ou masque
- erreur reseau

### 4.10 Ecran Configuration du cercle

**Objectif**

Permettre aux roles autorises de regler la cadence des reunions et d'ouvrir un cycle d'epargne.

**Donnees necessaires**

- meeting policy actuelle
- detail cercle
- fenetre et montant du futur cycle

**API utilisees**

- `GET /circles/{circleId}`
- `PATCH /circles/{circleId}/meeting-policy`
- `POST /circles/{circleId}/savings-cycles`

**Etats UI**

- lecture seule
- edition autorisee
- sauvegarde en cours
- creation de cycle reussie

**Actions utilisateur**

- changer la frequence
- creer un cycle
- enregistrer

**CTA**

- `Enregistrer la frequence`
- `Ouvrir un cycle`

**Erreurs possibles**

- permission insuffisante
- fenetre de cycle invalide
- cercle non gerable

### 4.11 Ecran Chat cercle

**Objectif**

Faire du chat le coeur relationnel du cercle.

**Donnees necessaires**

- `conversationId` du cercle
- messages
- statut delivered/read
- resume du cercle

**API utilisees**

- `GET /circles/{circleId}`
- `GET /chat/messages?conversationId=...&limit=30`
- `POST /chat/messages`
- `PATCH /chat/messages/{messageId}/read`
- `PATCH /chat/messages/{messageId}/delivered`

**Etats UI**

- conversation vide
- messages charges
- envoi en cours
- echec d'envoi

**Actions utilisateur**

- lire les messages
- envoyer un message
- reessayer un envoi

**CTA**

- `Envoyer`
- `Reessayer`

**Erreurs possibles**

- conversation introuvable
- message non envoye
- perte reseau

### 4.12 Ecran Membres du cercle

**Objectif**

Afficher les membres, leurs roles et les actions communautaires disponibles.

**Donnees necessaires**

- liste des membres
- coach / advisors
- role de l'utilisateur courant

**API utilisees**

- `GET /circles/{circleId}/members`
- `GET /circles/{circleId}/participants`
- `POST /circles/{circleId}/members/{memberAccountHolderId}/interactions`

**Etats UI**

- liste standard
- cercle avec coach/advisors
- action interaction en cours
- aucune donnee membre

**Actions utilisateur**

- ouvrir un membre
- encourager
- prier
- conseiller

**CTA**

- `Voir le profil`
- `Encourager`
- `Prier`
- `Conseiller`

**Erreurs possibles**

- membre introuvable
- limite quotidienne d'interaction
- erreur reseau

### 4.13 Ecran Profil membre

**Objectif**

Voir le detail d'un membre du cercle et sa situation financiere communautaire.

**Donnees necessaires**

- profil membre
- financial summary
- history timeline

**API utilisees**

- `GET /circles/{circleId}/members/{memberAccountHolderId}`
- `GET /circles/{circleId}/members/{memberAccountHolderId}/financials`
- `GET /circles/{circleId}/members/{memberAccountHolderId}/history?page=0&size=20`

**Etats UI**

- profil simple sans pret
- membre avec pret actif
- historique vide
- historique charge

**Actions utilisateur**

- lire l'historique
- revenir a la liste

**CTA**

- `Voir l'historique`
- `Retour aux membres`

**Erreurs possibles**

- membre introuvable
- historique indisponible

### 4.14 Ecran Meetings du cercle

**Objectif**

Afficher les reunions passees et futures avec un langage tres simple.

**Donnees necessaires**

- liste des reunions
- statut
- attendance count
- quorumReached
- notes

**API utilisees**

- `GET /circles/{circleId}/meetings`

**Etats UI**

- aucune reunion
- prochaine reunion programmee
- historique existant
- chargement

**Actions utilisateur**

- ouvrir une reunion
- declarer une reunion si role autorise

**CTA**

- `Declarer une reunion`
- `Voir les details`

**Erreurs possibles**

- cercle introuvable
- erreur reseau

### 4.15 Ecran Declarer une reunion

**Objectif**

Permettre au leader ou au role autorise d'enregistrer une reunion de facon tres rapide.

**Donnees necessaires**

- date et heure
- statut de reunion
- membres presents
- note courte

**API utilisees**

- `POST /circles/{circleId}/meetings`

**Etats UI**

- formulaire vide
- participants selectionnes
- enregistrement en cours
- succes

**Actions utilisateur**

- choisir la date
- cocher les presents
- ajouter une note
- valider

**CTA**

- `Enregistrer la reunion`

**Erreurs possibles**

- utilisateur non autorise
- donnees invalides
- cercle introuvable

### 4.16 Ecran Epargne du cercle

**Objectif**

Montrer la discipline d'epargne du cercle sans recreer une caisse collective.

**Donnees necessaires**

- etat du canal IMF personnel
- savings gate du cercle
- liste des cycles
- cycle en cours

**API utilisees**

- `GET /me/imf-savings-channel`
- `PUT /me/imf-savings-channel`
- `GET /circles/{circleId}/savings-gate`
- `GET /circles/{circleId}/savings-cycles`
- `POST /circles/{circleId}/savings-cycles`

**Etats UI**

- canal IMF non configure
- canal IMF configure mais non pret
- aucun cycle
- cycle ouvert
- gate epargne non prete
- gate epargne prete

**Actions utilisateur**

- configurer le canal IMF
- ouvrir un cycle si role autorise
- ouvrir le detail d'un cycle

**CTA**

- `Configurer mon canal IMF`
- `Ouvrir un cycle`
- `Voir mon cycle`

**Erreurs possibles**

- partenaire IMF invalide
- cycle non creable
- etat gate indisponible

### 4.17 Ecran Contribution du cycle

**Objectif**

Permettre au membre de payer sa contribution d'epargne avec un flow clair et guide.

**Donnees necessaires**

- detail du cycle
- contribution attendue du membre
- wallet status
- wallet balance
- resultat du payment intent
- resultat de confirmation

**API utilisees**

- `GET /circles/{circleId}/savings-cycles/{cycleId}/contributions`
- `POST /circles/{circleId}/savings-cycles/{cycleId}/contributions/me/payment-intents`
- `POST /circles/{circleId}/savings-cycles/{cycleId}/contributions/me/confirm`
- `GET /wallet/status`
- `GET /wallet/balance?currency=XOF`

**Etats UI**

- pas de contribution due
- contribution attendue
- wallet inactif
- pin manquant
- OTP requis
- payment intent cree
- contribution confirmee
- contribution deja reglee

**Actions utilisateur**

- verifier le montant attendu
- payer avec le wallet
- saisir pin
- saisir OTP si necessaire
- recharger l'etat apres confirmation

**CTA**

- `Contribuer maintenant`
- `Valider le paiement`
- `Saisir le code OTP`

**Erreurs possibles**

- montant superieur au reste du
- devise invalide
- provider non supporte
- solde insuffisant
- pin incorrect
- OTP invalide ou expire
- contribution deja confirmee

### 4.18 Ecran Progression du cercle

**Objectif**

Rendre visible la progression collective vers la confiance et la readiness.

**Donnees necessaires**

- trust score detail
- seed capital eligibility
- credit candidate state du membre
- coach assignment
- network coaching
- savings gate

**API utilisees**

- `GET /circles/{circleId}/trust-score`
- `GET /circles/{circleId}/seed-capital-eligibility`
- `GET /circles/{circleId}/credit-candidate/me`
- `GET /circles/{circleId}/coach-assignment`
- `GET /circles/{circleId}/network-coaching`
- `GET /circles/{circleId}/savings-gate`

**Etats UI**

- cercle loin de l'eligibilite
- cercle proche de l'eligibilite
- cercle pret pour futures gates
- membre non autorise au credit
- membre candidat au credit

**Actions utilisateur**

- lire les blocages
- ouvrir les ecrans d'action
- contacter le coach

**CTA**

- `Voir ce qui manque`
- `Aller a l'epargne`
- `Preparer mon projet`

**Erreurs possibles**

- score indisponible
- eligibility introuvable
- erreur reseau

### 4.19 Ecran Eligibilite Seed Capital

**Objectif**

Montrer au membre s'il peut passer a la demande seed capital et pourquoi.

**Donnees necessaires**

- member access state
- reason codes
- readiness state
- trust score
- savings gate state
- max eligible amount
- pending criteria

**API utilisees**

- `GET /circles/{circleId}/credit-candidate/me`
- `GET /circles/{circleId}/seed-capital-eligibility`

**Etats UI**

- non eligible
- eligible bientot
- candidat pret
- bloque par access policy

**Actions utilisateur**

- comprendre les blocages
- ouvrir la progression
- commencer la demande

**CTA**

- `Commencer ma demande`
- `Voir mes criteres`
- `Continuer mes actions`

**Erreurs possibles**

- cercle non eligible
- membre non autorise
- erreur reseau

### 4.20 Ecran Demande seed capital

**Objectif**

Permettre la saisie simple du projet et la soumission unique de la demande canonique.

**Donnees necessaires**

- circleId
- maxEligibleAmount
- requestedAmount
- requestedDurationMonths
- requestedInterestRate
- bloc `project`

**API utilisees**

- `GET /circles/{circleId}/credit-candidate/me`
- `POST /credit-applications/seed-capital`

**Etats UI**

- formulaire vide
- montant au-dessus du maximum
- projet incomplet
- pret a soumettre
- demande envoyee
- demande dupliquee existante

**Actions utilisateur**

- saisir le montant
- saisir la duree
- saisir le projet
- soumettre

**CTA**

- `Preparer mon projet`
- `Soumettre ma demande`

**Erreurs possibles**

- montant au-dessus du maximum eligible
- demande active deja existante
- candidat pas encore autorise
- erreur reseau

### 4.21 Ecran Statut de demande financement

**Objectif**

Suivre l'evolution d'une demande seed capital apres envoi.

**Donnees necessaires**

- liste des demandes
- detail d'une demande
- workflowState
- bridgeState
- linkedLoanId si present

**API utilisees**

- `GET /credit-applications/me`
- `GET /credit-applications/{applicationId}`

**Etats UI**

- aucune demande
- `SUBMITTED`
- `UNDER_REVIEW`
- `APPROVED`
- `WAITING_FUTURE_GATES`
- `BLOCKED`
- demande liee a un pret

**Actions utilisateur**

- ouvrir une demande
- lire le statut
- aller vers le remboursement si un pret existe

**CTA**

- `Voir le detail`
- `Voir mon pret`

**Erreurs possibles**

- demande introuvable
- acces refuse
- erreur reseau

### 4.22 Ecran Mes prets et remboursement

**Objectif**

Permettre au membre de suivre ses prets et de payer une echeance.

**Donnees necessaires**

- liste des prets
- schedules
- montant a payer
- wallet status
- wallet balance
- resultat remboursement

**API utilisees**

- `GET /loans/me`
- `POST /loans/{loanId}/repay`
- `GET /wallet/status`
- `GET /wallet/balance?currency=XOF`
- `GET /api/mobile/wallet/dashboard`

**Etats UI**

- aucun pret
- pret actif
- pret approuve non decaisse
- remboursement OTP requis
- remboursement enregistre
- solde insuffisant

**Actions utilisateur**

- ouvrir un pret
- payer une echeance
- saisir pin
- saisir OTP si necessaire

**CTA**

- `Payer mon echeance`
- `Valider le code`

**Erreurs possibles**

- montant invalide
- pin incorrect
- OTP invalide
- solde insuffisant
- pret introuvable

### 4.23 Ecran Profil membre / Financial profile

**Objectif**

Donner au membre sa vue personnelle: membership, wallet, access policy et statut IMF.

**Donnees necessaires**

- financial profile
- statut wallet
- solde wallet

**API utilisees**

- `GET /me/financial-profile`
- `GET /wallet/status`
- `GET /wallet/balance?currency=XOF`
- `GET /api/mobile/wallet/dashboard`

**Etats UI**

- membership actif
- membership expirant
- membership inactif
- wallet actif
- wallet inactif
- IMF ready ou non ready
- financial access allowed ou denied

**Actions utilisateur**

- voir le statut
- ouvrir wallet
- ouvrir canal IMF
- renouveler le membership

**CTA**

- `Renouveler mon membership`
- `Activer mon wallet`
- `Configurer mon canal IMF`

**Erreurs possibles**

- profil indisponible
- wallet indisponible

### 4.24 Ecran Canal IMF

**Objectif**

Configurer le canal IMF personnel necessaire a la discipline d'epargne et a la readiness.

**Donnees necessaires**

- partenaire selectionne
- references externes
- readiness note
- etat courant du canal

**API utilisees**

- `GET /me/imf-savings-channel`
- `PUT /me/imf-savings-channel`
- `GET /me/financial-profile`

**Etats UI**

- non configure
- configure
- actif
- non pret
- mise a jour reussie

**Actions utilisateur**

- choisir un partenaire
- saisir les references
- enregistrer

**CTA**

- `Enregistrer mon canal IMF`
- `Mettre a jour`

**Erreurs possibles**

- partenaire invalide
- reference manquante
- erreur reseau

### 4.25 Ecran Wallet

**Objectif**

Afficher le statut du wallet, le solde et les operations recentes pour rassurer l'utilisateur avant tout paiement.

**Donnees necessaires**

- wallet active ou non
- pin configure ou non
- balance
- operations recentes

**API utilisees**

- `GET /wallet/status`
- `POST /wallet/activate`
- `GET /wallet/balance?currency=XOF`
- `POST /wallet/pin`
- `GET /api/mobile/wallet/dashboard`
- `GET /api/mobile/wallet/operations`

**Etats UI**

- wallet inactif
- wallet actif sans pin
- wallet actif avec pin
- solde zero
- operations recentes

**Actions utilisateur**

- activer le wallet
- configurer le pin
- consulter les operations

**CTA**

- `Activer mon wallet`
- `Configurer mon code`
- `Voir mes operations`

**Erreurs possibles**

- wallet non eligible
- pin invalide
- erreur reseau

## 5. Etats cles UI

| Etat | Signification produit | Comportement UI attendu | CTA principal |
| --- | --- | --- | --- |
| Onboarding incomplet | le membre n'a pas termine pays, plan, verification ou activation | bloquer l'entree shell et montrer l'etape courante | `Continuer` |
| Membership inactif | le membre ne peut pas profiter de tout le produit | bannieres visibles sur Accueil et Profil | `Activer mon membership` |
| Wallet inactif | impossible de payer contribution, remboursement ou abonnement | bloquer les paiements et expliquer pourquoi | `Activer mon wallet` |
| Pin wallet manquant | le wallet existe mais les paiements ne doivent pas demarrer | demander le parametrage avant tout paiement | `Configurer mon code` |
| Cercle incomplet | le cercle n'a pas la taille cible ou la discipline attendue | montrer un etat pedagogique | `Voir ce qui manque` |
| Cercle actif | le cercle est pret pour la routine communautaire | ouvrir chat, meetings, epargne, progression | `Ouvrir le chat` |
| Aucun cycle d'epargne | pas encore de discipline d'epargne formalisee | pousser creation ou attente du leader | `Ouvrir un cycle` |
| Contribution attendue | le membre doit payer sur un cycle ouvert | mettre l'action en avant sur Accueil, Cercle et Epargne | `Contribuer maintenant` |
| Contribution confirmee | le membre est a jour sur le cycle | afficher un etat positif et calme | `Voir le cycle` |
| Savings gate non prete | le cercle n'a pas encore les preconditions IMF/epargne | expliquer le nombre de canaux ou cycles manquants | `Voir l'epargne` |
| Candidat seed capital | le membre peut soumettre sa demande | rendre l'entree financement tres visible | `Soumettre ma demande` |
| Demande envoyee | la demande a ete soumise | afficher statut, date et etape suivante | `Voir le detail` |
| Demande en review | la demande avance cote institution | rassurer et eviter les doubles soumissions | `Suivre ma demande` |
| Financement obtenu | un pret existe et doit etre suivi | basculer vers la gestion du remboursement | `Voir mon pret` |

## 6. CTA produit

| CTA | Ecran principal | Condition d'affichage | Resultat attendu |
| --- | --- | --- | --- |
| `Rejoindre un cercle` | Liste cercles / parcours invitation | utilisateur sans cercle cible | entree dans la vie communautaire |
| `Creer un cercle` | Liste cercles | utilisateur eligible sans cercle | creation d'un nouveau cercle |
| `Ouvrir le chat` | Detail cercle | cercle accessible | entree dans la conversation communautaire |
| `Tenir une reunion` | Meetings | role autorise | enregistrement d'une reunion |
| `Configurer mon canal IMF` | Epargne / Profil | canal non configure ou non pret | readiness IMF amelioree |
| `Ouvrir un cycle` | Configuration cercle / Epargne | role autorise | creation d'un cycle d'epargne |
| `Contribuer maintenant` | Accueil / Epargne / Contribution | contribution attendue | creation d'un payment intent puis confirmation |
| `Voir ce qui manque` | Progression / Eligibilite | cercle ou membre non pret | comprehension claire des blocages |
| `Preparer mon projet` | Eligibilite seed capital | candidat proche ou pret | entree dans le formulaire seed capital |
| `Soumettre ma demande` | Demande seed capital | donnees valides et membre candidat | creation de `CreditApplication` |
| `Payer mon echeance` | Mes prets et remboursement | pret actif avec echeance | remboursement enregistre |
| `Renouveler mon membership` | Profil | membership expirant ou inactif | reprise du flow d'activation |

## 7. Notifications et messages systeme

| Evenement | Message recommande | Deep link cible | Ton attendu |
| --- | --- | --- | --- |
| Contribution confirmee | `Votre contribution du cycle a ete confirmee.` | detail du cycle | rassurant |
| Paiement contribution OTP | `Entrez le code recu pour finaliser votre contribution.` | ecran contribution | direct |
| Reunion tenue | `La reunion du cercle a bien ete enregistree.` | meetings du cercle | positif |
| Cercle proche eligibilite | `Votre cercle se rapproche du seed capital. Continuez vos actions.` | progression du cercle | motivant |
| Canal IMF non pret | `Votre canal IMF doit etre complete pour avancer.` | ecran canal IMF | explicatif |
| Demande envoyee | `Votre demande seed capital a ete envoyee.` | detail de la demande | rassurant |
| Demande en review | `Votre demande est en cours d'etude.` | detail de la demande | calme |
| Demande approuvee | `Bonne nouvelle: votre financement a ete approuve.` | mes prets | celebrant sobre |
| Membership expirant | `Votre membership arrive bientot a expiration.` | activation membership | preventif |
| Wallet inactif | `Activez votre wallet pour payer plus facilement.` | wallet | utile |

### Regles de wording

- preferer des phrases courtes
- dire ce que l'utilisateur peut faire maintenant
- ne pas exposer le jargon backend
- remplacer les formulations techniques par des formulations d'action

## 8. UX Guidelines

### 8.1 Principes generaux

- boutons larges, hauteur tactile confortable
- un seul CTA principal par ecran
- hierarchie visuelle tres nette
- icones simples et comprehensibles
- pas d'ecrans surcharges
- vocabulaire courant plutot que financier

### 8.2 Style des parcours

- toujours montrer `ce que je dois faire maintenant`
- decouper les paiements en etapes courtes
- afficher un recapitulatif avant validation finale
- confirmer chaque succes avec un message simple
- utiliser des progressions visuelles sur readiness et seed capital

### 8.3 Regles de comprehension

- expliquer les blocages en langage simple
- ne jamais afficher une liste brute de codes backend
- convertir les etats techniques en phrases produit
- montrer les nombres importants:
  - montant du cycle
  - montant restant
  - score global
  - nombre de criteres manquants

### 8.4 Regles de resilience

- prevoir un etat `hors ligne / impossible de charger`
- afficher le dernier contenu deja charge quand c'est possible
- ne pas perdre les formulaires longs si le reseau coupe
- generer des `idempotencyKey` cote mobile pour les parcours sensibles:
  - activation membership
  - payment intent contribution
  - remboursement de pret

### 8.5 Regles de role et permissions

- masquer les actions non autorisees plutot que laisser l'utilisateur echouer
- si le backend refuse quand meme l'action, montrer un message simple:
  - `Cette action n'est pas disponible pour votre role.`
- considerer certains `404` comme des ressources non accessibles et non comme des bugs visibles

### 8.6 Regles specifiques finance

- toujours afficher le currency code
- demander le PIN seulement au moment du paiement
- gerer les flows OTP comme une deuxieme etape du meme parcours
- ne pas lancer deux fois une meme operation si un idempotencyKey est deja en cours

## 9. Regles d'integration Flutter

### 9.1 Source de verite API

- le contrat prioritaire reste `docs/mobile_api_contract_v1.md`
- les endpoints proposes dans ce PRD doivent rester alignes avec les routes deja presentes
- ne pas creer de dependance mobile a des APIs legacy non documentees

### 9.2 Strategie de data layer

- normaliser `ApiEnvelope` et `CommonResponse` dans un client reseau unique
- modeliser explicitement les etats suivants:
  - `loading`
  - `success`
  - `empty`
  - `actionRequired`
  - `otpRequired`
  - `error`

### 9.3 Ordre de priorite de build Flutter

1. onboarding et activation membership
2. accueil, notifications et liste des cercles
3. detail cercle, chat, meetings et membres
4. epargne, contribution et canal IMF
5. progression, eligibilite et demande seed capital
6. prets, remboursement, profil et wallet

### 9.4 Non-goals Flutter

- ne pas implementer de logique de caisse de cercle
- ne pas creer une page projet separee du flow seed capital
- ne pas implementer un live meeting
- ne pas reutiliser `POST /loans` comme entree seed capital
