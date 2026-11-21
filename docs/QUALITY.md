# Swagger UI, tests, couverture et Sonar

## Ce que chaque outil apporte

| Outil | Fonction | Preuve attendue |
|---|---|---|
| JUnit | Exécute les tests Java et leurs assertions | Rapports Surefire et Failsafe |
| JaCoCo | Mesure les lignes et branches Java exercées pendant les tests | HTML et XML dans `target/site/jacoco/` |
| Checkstyle | Vérifie les quelques règles statiques configurées | Étape Maven `validate` |
| SonarQube Cloud | Analyse statique, problèmes de fiabilité / sécurité / maintenabilité, duplications et import de couverture | Analyse distante et Quality Gate réels |
| Swagger UI | Affiche le contrat OpenAPI et permet d'appeler l'API | Interface HTTP, contrat et réponses réelles |

Une ligne couverte a été exécutée par un test ; cela ne prouve ni toutes ses conditions, ni l'absence de défaut. Sonar importe le XML JaCoCo : il ne lance pas les tests et ne fabrique pas la couverture. Ne pas déclarer un Quality Gate réussi tant qu'une analyse authentifiée n'a pas été exécutée.

## Utiliser Swagger

Démarrer le backend et sa base selon le README, puis ouvrir :

http://localhost:8080/swagger-ui.html

Cette adresse redirige vers http://localhost:8080/api/docs/index.html. Le contrat chargé est http://localhost:8080/api/docs/openapi.yaml. Le seul fichier source est `docs/openapi.yaml`, copié dans le classpath par Maven ; éviter une deuxième copie manuelle qui divergerait. Les ressources Swagger sont embarquées via WebJar, sans CDN et sans envoi du contrat à un validateur distant.

1. Tester `GET /catalog/films` avec « Try it out » puis « Execute ».
2. Pour un parcours privé, exécuter `POST /auth/login` avec un compte de démonstration et le mot de passe initialisé via `DEMO_PASSWORD`.
3. Exécuter `GET /auth/me` puis les opérations autorisées pour ce rôle. Le navigateur conserve la session HttpOnly ; le bouton « Authorize » n'est pas nécessaire pour saisir un cookie.
4. L'initialiseur récupère un jeton CSRF frais avant chaque POST / PUT / PATCH / DELETE. La protection CSRF reste activée, y compris pour la connexion et la déconnexion. Les appels sont limités à l'API de la même origine.
5. Pour changer de compte, appeler `POST /auth/logout`, puis se reconnecter.

Les écritures de Swagger modifient réellement la base ; utiliser les données fictives de démonstration. Dans un déploiement via le proxy frontend, utiliser `/api/docs/index.html` sur cette même origine ; le cookie de l'API directe et celui du frontend appartiennent à des origines différentes. Le déploiement public est distinct des tests locaux ; utiliser l’URL du déploiement concerné.

## Lire JaCoCo sur macOS

```bash
./mvnw clean verify
open target/site/jacoco/index.html
```

Ce premier résultat ne contient que les tests unitaires. Pour la couverture des parcours PostgreSQL et HTTP, lancer la commande `-Pdatabase-tests clean verify` du README avec une base de test isolée, puis ouvrir le même rapport. `clean` efface les anciens compteurs. Le XML destiné à Sonar est `target/site/jacoco/jacoco.xml`.

Les tests Node de l'initialiseur Swagger se lancent avec :

```bash
node --test scripts/swagger-request.test.mjs
```

Ils sont distincts du pourcentage Java de JaCoCo. Les tests HTTP Java vérifient la distribution des ressources Swagger et le maintien des protections d'accès.

## Activer SonarQube Cloud dans GitHub

Le workflow exige un jeton et les clés exactes du projet. Choisir une organisation SonarQube Cloud en région Europe, importer le dépôt GitHub backend et suivre son assistant **GitHub Actions / Maven**. Utiliser l'analyse CI ; désactiver l'analyse automatique si elle est proposée / activée, afin de ne pas lancer deux méthodes d'analyse concurrentes.

Dans le dépôt GitHub, `Settings → Secrets and variables → Actions` :

| Type | Nom | Valeur |
|---|---|---|
| Repository secret | `SONAR_TOKEN` | Jeton autorisé à analyser ce projet, créé dans Sonar |
| Repository variable | `SONAR_ORGANIZATION` | Clé exacte de l'organisation affichée par Sonar |
| Repository variable | `SONAR_PROJECT_KEY` | Clé exacte du projet backend affichée par Sonar |

Ne jamais mettre le jeton dans Git, dans le PDF, dans une capture ou dans un message. Le workflow l'utilise uniquement depuis les secrets GitHub. Sur `main` et les PR internes vers `main`, des valeurs manquantes font échouer l’étape avec un message explicite. Il n’existe plus de drapeau `SONAR_ENABLED` permettant de masquer silencieusement l’analyse.

La configuration vise l'offre Free pour un dépôt public : analyse de `main` et des PR internes ciblant `main`. Les branches de fonctionnalité et les PR vers `develop` conservent les tests et Checkstyle, mais n'exécutent pas Sonar. Une autre offre serait nécessaire pour étendre les analyses selon ses conditions. Ne pas contourner cette limite en prétendant qu'une analyse de fonctionnalité est une analyse de `main`.

Publier et valider le projet complet dans `main` avec les vraies PR décrites dans GIT-WORKFLOW. Initialiser d'abord l'analyse de `main` ; les PR suivantes vers `main` auront alors cette référence. L'étape Sonar attend le Quality Gate jusqu'à 300 secondes et échoue si la porte qualité échoue. Pour bloquer effectivement une fusion, ajouter le check du workflow aux règles de protection de branche GitHub : la présence du YAML ne configure pas ces règles à elle seule.

La CI génère d'abord les rapports de tests et de couverture avec PostgreSQL 17, puis lance le scanner Maven dont la version est fixée dans `pom.xml`. Elle ne reçoit aucun secret pour les PR provenant d'un fork. Les artefacts de rapports sont téléchargeables dans GitHub Actions.

Après la première analyse : examiner les problèmes, traiter les vrais défauts, expliquer les éventuels faux positifs, et conserver l'URL du tableau de bord et le résultat du Quality Gate pour le dossier. Ne pas exclure du code métier simplement pour augmenter artificiellement la couverture. Une analyse distante et son Quality Gate doivent être vérifiés sur le commit livré. Un résultat local ou une ancienne capture ne prouve pas cette validation.

## Références officielles

- Swagger UI : https://swagger.io/docs/open-source-tools/swagger-ui/usage/installation/
- Sonar et couverture Java : https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/java-test-coverage
- Scanner Maven : https://docs.sonarsource.com/sonarqube-cloud/advanced-setup/ci-based-analysis/sonarscanner-for-maven
- Offres Sonar : https://docs.sonarsource.com/sonarqube-cloud/administering-sonarqube-cloud/managing-subscription/subscription-plans
