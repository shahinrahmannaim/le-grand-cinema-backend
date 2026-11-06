# Le Grand Cinéma — Backend

Application Studi Bloc 3 : Java 21, Spring Boot 4.1.2, Maven Wrapper, PostgreSQL 17, Spring Security, Spring Session JDBC et Flyway. Frontend séparé : https://github.com/shahinrahmannaim/le-grand-cinema-frontend.

Le code couvre les 15 histoires du backlog : catalogue, séances, sièges en direct, compte client, confirmation atomique et idempotente, guichet, billets privés, films / programmation, recherche, occupation et analyses. Aucun paiement en ligne ; paiement au cinéma. Les règles d’annulation et de non-présentation restent à valider, donc aucune annulation client n’est exposée. La réservation n’est pas une mesure d’entrée réelle.

## Démarrage IntelliJ / macOS

JDK **21** pour le projet Maven. Ouvrir `pom.xml` dans IntelliJ et sélectionner ce JDK. Démarrer Docker Desktop, puis dans ce dossier :

```bash
cp .env.example .env
docker compose up -d --wait
```

Ne pas écraser un `.env` déjà personnalisé. Le fichier est ignoré par Git. La base écoute seulement sur `127.0.0.1:5433`.

Dans Run → Edit Configurations → Environment variables :

```text
DB_URL=jdbc:postgresql://localhost:5433/legrandcinema
DB_USERNAME=cinema
DB_PASSWORD=local-development-change-me
SPRING_PROFILES_ACTIVE=demo
DEMO_PASSWORD=local-demo-password-2026
SECURE_COOKIES=false
```

`DB_PASSWORD` doit correspondre à `POSTGRES_PASSWORD` dans `.env`. Les exemples ci-dessus servent uniquement en local. Spring Boot ne charge pas automatiquement le `.env` de Docker Compose. Lancer `CinemaBackendApplication`.

Alternative terminal :

```bash
DB_PASSWORD=local-development-change-me DEMO_PASSWORD=local-demo-password-2026 SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

Santé : http://localhost:8080/actuator/health. Le frontend démarre sur http://localhost:4200 et relaie `/api` vers ce backend. Utiliser l’URL frontend pour la connexion et la réservation.

Le profil `demo` initialise trois films fictifs, les comptes ci-dessous et des séances sur 30 jours. À chaque démarrage, il complète les créneaux futurs manquants sans écraser les réservations, les séances annulées ni les conflits de programmation. Les dix salles et leurs 1 700 places sont créées par migration. Ne jamais modifier une migration appliquée. `V1002` suit l’ancienne migration de démonstration `V1001` pour permettre une mise à jour du premier incrément.

| Compte | Rôle |
|---|---|
| `client@demo.cinema.test` | Client : réservations et ses billets |
| `staff@demo.cinema.test` | Guichet, suivi et analyses |
| `admin@demo.cinema.test` | Administration et fonctions du personnel |

Le mot de passe commun est la valeur choisie dans `DEMO_PASSWORD` au premier démarrage. Changer la variable ne remplace pas le hash d’un compte existant. Ne jamais utiliser de données personnelles ou de mots de passe réels pour cette démonstration publique.

## Tests et qualité

Sans base :

```bash
./mvnw verify
```

Avec une base de test **isolée** :

```bash
docker compose exec postgres createdb -U cinema cinema_test
DB_URL=jdbc:postgresql://localhost:5433/cinema_test DB_PASSWORD=local-development-change-me DEMO_PASSWORD=test-demo-password-2026 ./mvnw -Pdatabase-tests clean verify
```

Ne pas recréer une base déjà existante. Les tests d’intégration écrivent des données fictives ; certains les nettoient, d’autres conservent des fixtures. Ne jamais viser la base de production. La CI utilise un PostgreSQL 17 jetable. Les tests HTTP vérifient aussi la session JDBC, CSRF et les rôles. La concurrence teste 20 clients réels au niveau du service transactionnel, avec la vraie base.

Rapports : `target/surefire-reports`, `target/failsafe-reports`, `target/site/jacoco/index.html`. Checkstyle vérifie les imports, l’absence d’imports globaux et les noms de fichiers. Ces règles constituent un contrôle statique limité, pas un audit complet de sécurité. Les résultats effectivement obtenus et les limites de vérification figurent dans [VALIDATION](docs/VALIDATION.md).

## Documentation

- [Contrat OpenAPI](docs/openapi.yaml) : tous les endpoints, rôles, erreurs et corps JSON.
- [Architecture et modèle](docs/ARCHITECTURE.md), schéma SQL dans `src/main/resources/db/migration/`.
- [Traçabilité US01–US15](docs/STORIES.md), [recette](docs/ACCEPTANCE.md).
- [Déploiement Render / Neon / Cloudflare](docs/DEPLOYMENT.md).
- [Flux Git et actions externes restantes](docs/GIT-WORKFLOW.md).
- [Bug de confirmation et non-régression](docs/BUG-001.md).

Deux dépôts permettent des déploiements indépendants. Le frontend public utilise un proxy `/api` de même origine pour les cookies. Le Dockerfile du backend n’embarque pas Angular ; PostgreSQL reste externe. Aucune preuve de mise en ligne, de PR GitHub ou de recette métier n’est déduite de la simple présence d’une configuration.
