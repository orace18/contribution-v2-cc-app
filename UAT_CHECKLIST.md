# 🧪 CC APP — QA MÉTIER (UAT) CHECKLIST

**Date :**  
**Responsable :**  
**Environnement :** (Staging / Preprod)

---

# 🎯 OBJECTIF

Valider que :

- ✔ Les configurations sont correctement appliquées
- ✔ Les règles métier sont respectées
- ✔ Les flux financiers sont cohérents
- ✔ Le backoffice est utilisable par les opérations
- ✔ Le système fonctionne en conditions réelles

---

# 1️⃣ COUNTRY CONFIG ENGINE

## ✅ Test 1 — Création pays

**Steps :**
- Créer un nouveau pays (ex: CI)
- Vérifier apparition dans la liste

**Résultat attendu :**
- Visible uniquement après initialisation
- Aucune config par défaut incorrecte

---

## ✅ Test 2 — Modification config MLM

**Steps :**
- Modifier taux (ex: 30% → 25%)
- Activer nouvelle version

**Résultat attendu :**
- Nouvelle valeur appliquée immédiatement
- Ancienne version non impactée

---

## ✅ Test 3 — Versioning

**Steps :**
- Créer v1
- Créer v2
- Réactiver v1

**Résultat attendu :**
- Le système utilise bien v1

---

## ✅ Test 4 — Effective Date

**Steps :**
- Créer config avec date future

**Résultat attendu :**
- Non active aujourd’hui
- Active automatiquement à la date prévue

---

# 2️⃣ MLM

## ✅ Test 5 — Calcul commissions

**Steps :**
- Simuler paiement abonnement
- Vérifier distribution

**Résultat attendu :**
- Montants corrects
- Non éligible → non distribué

---

## ✅ Test 6 — Cap hebdomadaire

**Steps :**
- Dépasser le plafond

**Résultat attendu :**
- Cap respecté
- Excédent non payé

---

# 3️⃣ FINANCING (IMF)

## ✅ Test 7 — Offre par pays

**Steps :**
- Activer offre pour BJ
- Désactiver pour CI

**Résultat attendu :**
- Visible uniquement dans BJ

---

## ✅ Test 8 — Workflow application

**Steps :**
- Soumettre demande

**Résultat attendu :**
- SUBMITTED → UNDER_REVIEW → APPROVED

---

## ✅ Test 9 — Disbursement

**Résultat attendu :**
- Wallet crédité
- Ledger correct
- Partner ledger correct

⚠️ Vérifier cohérence Wallet vs Ledger

---

## ✅ Test 10 — Repayment

**Steps :**
- Effectuer remboursement

**Résultat attendu :**
- Ledger mis à jour
- Settlement pris en compte

---

# 4️⃣ CERCLES

## ✅ Test 11 — Création cercle

**Résultat attendu :**
- Rôles corrects
- Coach = sponsor MLM
- Advisors ajoutés automatiquement

---

## ✅ Test 12 — Règles cercle

**Résultat attendu :**
- Min = 3
- Max = 5
- Max assisted respecté

---

## ✅ Test 13 — Cycle crédit

**Résultat attendu :**
- Ordre respecté
- Blocage en cas de défaut

---

# 5️⃣ CONFIG SNAPSHOT (CRITIQUE)

## ✅ Test 14 — Snapshot config

**Steps :**
- Créer crédit
- Modifier config
- Vérifier ancien crédit

**Résultat attendu :**
- Ancien crédit utilise ancienne config

---

# 6️⃣ BACKOFFICE

## ✅ Test 15 — UX config

**Résultat attendu :**
- Pas de JSON brut
- Formulaires clairs

---

## ✅ Test 16 — Full config

**API :**
GET /admin/country-config/{country}/full

**Résultat attendu :**
- Toutes les configs visibles

---

## ✅ Test 17 — RBAC

**Steps :**
- Login partner IMF

**Résultat attendu :**
- Accès limité à ses données

---

# 7️⃣ PERFORMANCE

## ✅ Test 18 — Cache config

**Résultat attendu :**
- Pas de requêtes DB répétées

---

## ✅ Test 19 — Charge

**Steps :**
- Simuler 100 utilisateurs

**Résultat attendu :**
- Pas de ralentissement

---

# 8️⃣ EDGE CASES

## ✅ Test 20 — Config invalide

**Cas :**
- MLM > 100%

**Résultat attendu :**
- Erreur bloquante

---

## ✅ Test 21 — Pays non configuré

**Résultat attendu :**
- Fallback platform OU erreur claire

---

# 9️⃣ AUDIT

## ✅ Test 22 — Logs

**Résultat attendu :**
- Historique complet
- Qui / Quand / Quoi

---

# 🔟 VALIDATION FINALE

## ✅ Critères GO LIVE

- [ ] Config fiable
- [ ] MLM correct
- [ ] Ledger cohérent
- [ ] Financing OK
- [ ] Multi-pays OK
- [ ] Multi-IMF OK
- [ ] UX Backoffice validée

---

# 🚨 POINTS CRITIQUES

1. Ledger vs Wallet
2. Config Snapshot
3. Calcul MLM

👉 Si ces 3 sont bons → système solide

---

# 📝 NOTES / BUGS

| ID | Description | Gravité | Statut |
|----|------------|--------|--------|
|    |            |        |        |

---

# ✅ SIGN-OFF

**Produit :**  
**Tech :**  
**Ops :**  
**Date :**

---