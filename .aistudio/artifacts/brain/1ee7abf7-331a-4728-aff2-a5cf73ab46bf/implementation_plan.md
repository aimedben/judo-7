# Architecture Offline-First & Synchronisation Supabase — Judo Club Seddouk

Préparation de la version finale de Judo Club Seddouk intégrant Supabase PostgreSQL comme base centrale en ligne, tout en garantissant une réactivité locale absolue (< 50 ms par scan) via SQLite/Room, un fonctionnement 100% hors-ligne sans dépendance réseau critique, et le respect strict des limites de l'offre gratuite Supabase (aucun abonnement payant, aucun socket Realtime permanent).

---

## Décisions Clés & Arbitrages Validés

> [!IMPORTANT]
> Les choix d'architecture ont été validés selon vos réponses :
> - **Configuration Supabase** : Saisie et modification directement dans les **Paramètres Administrateur** de l'application (chiffré localement) avec secours via variables d'environnement (`.env` / `BuildConfig`).
> - **Résolution de conflits** : Règle du **dernier horodatage valide (Last-Write-Wins)** avec enregistrement systématique dans un **journal d'audit local et distant**.
> - **Fréquence de synchronisation** : Déclenchement automatique **lors de la fermeture de session** (dialogue de confirmation dédié) et **chaque jour à 22h00**, complété par un bouton de synchronisation manuelle à la demande.
> - **Respect du forfait gratuit Supabase** : Désactivation totale de Supabase Realtime (évite l'épuisement des quotas de connexions concurrentes et de messages). Utilisation exclusive de l'API REST PostgREST avec `upsert` idempotent par lots.

---

## 1. Vue d'Ensemble & Fonctionnement

### Expérience Utilisateur
1. **Sur le Tatami (Mode Coach & Scanner)** :
   - Pointage instantané des judokas par QR code, carte sportive ou douchette USB sans jamais interroger Internet.
   - Les présences et séances sont écrites immédiatement dans SQLite (Room) en mémoire locale.
   - Une opération est ajoutée silencieusement à la file de synchronisation persistante (`sync_queue`).
2. **Synchronisation en Fin de Journée (22h00 ou Fermeture)** :
   - Dialogue élégant lors de la déconnexion ou fermeture présentant le bilan des modifications en attente.
   - Possibilité de lancer la synchronisation en un clic avec barre de progression par table.
   - Possibilité de quitter sans synchroniser après confirmation explicite de l'administrateur.
3. **Espace Administrateur & Cloud** :
   - Tableau de bord de statut de synchronisation : date et heure du dernier sync réussi, nombre d'opérations en file d'attente, statut de connectivité et consommation estimée.
   - Export de sauvegarde complet (format JSON clair et documenté) et outil de restauration avec contrôle d'intégrité préalable.

---

## 2. Expérience Utilisateur & Nouveaux Composants UI

### Écran de Sortie & Fermeture de Session (`QuitSyncDialog`)
- **Indicateurs affichés** :
  - Nombre d'opérations en attente (ex: `14 présences, 1 séance`).
  - Date et heure de la dernière synchronisation confirmée par Supabase.
  - État de la connexion Internet (En ligne / Hors ligne).
- **Actions disponibles** :
  - `[ Synchroniser et Quitter ]` (action recommandée en vert JudoSuccess si réseau disponible).
  - `[ Quitter sans synchroniser ]` (affiche une alerte de sécurité sur les données non répliquées).
  - `[ Continuer dans l'application ]` (annule la fermeture).

### Panneau de Contrôle Cloud & Supabase (`AdminCloudSyncScreen`)
- **Statut de liaison** : Indicateur vert/rouge de connexion au projet Supabase Seddouk.
- **Paramètres de connexion** :
  - URL du projet (`https://xyz.supabase.co`).
  - Clé publique `anon` (jamais de clé secrète `service_role`).
  - Bouton de test de connectivité immédiat.
- **Bouton d'action principale** : `[ Synchroniser maintenant ]` avec animation et rapport détaillé (X envoyés, Y reçus, Z conflits résolus).
- **Gestionnaire de Sauvegardes & Restauration** :
  - Téléchargement d'une archive JSON horodatée `judo_seddouk_backup_YYYYMMDD_HHmm.json`.
  - Import et restauration d'un fichier de sauvegarde avec vérification de cohérence avant écrasement.

---

## 3. Schéma Supabase PostgreSQL & Sécurité (RLS)

Le projet intégrera les migrations SQL versionnées à déposer dans `supabase/migrations/` :

```sql
-- 1. Table des catégories de judo
CREATE TABLE IF NOT EXISTS public.judo_categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    group_code TEXT NOT NULL,
    coach1_name TEXT,
    coach2_name TEXT,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Table des coachs
CREATE TABLE IF NOT EXISTS public.coaches (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    grade TEXT NOT NULL,
    phone TEXT,
    assigned_categories JSONB DEFAULT '[]'::jsonb,
    is_active BOOLEAN DEFAULT true,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Table des adhérents / judokas
CREATE TABLE IF NOT EXISTS public.members (
    id BIGINT PRIMARY KEY, -- ID stable aligné sur Room
    qr_code TEXT NOT NULL UNIQUE,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    birth_date TEXT,
    phone TEXT,
    group_code TEXT NOT NULL,
    belt_name TEXT NOT NULL,
    status TEXT DEFAULT 'ACTIF',
    photo_path TEXT, -- Chemin relatif dans le bucket Storage
    medical_certif_valid BOOLEAN DEFAULT true,
    registration_date TEXT,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. Table des créneaux de planning hebdomadaire
CREATE TABLE IF NOT EXISTS public.scheduled_sessions (
    id TEXT PRIMARY KEY,
    category_id TEXT REFERENCES public.judo_categories(id),
    category_name TEXT NOT NULL,
    group_code TEXT NOT NULL,
    day_of_week_name TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    coach_names JSONB DEFAULT '[]'::jsonb,
    is_recurring_weekly BOOLEAN DEFAULT true,
    series_id TEXT,
    notes TEXT,
    status TEXT DEFAULT 'SCHEDULED',
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. Table des séances effectuées (Historique Tatami)
CREATE TABLE IF NOT EXISTS public.past_sessions (
    id TEXT PRIMARY KEY,
    category_id TEXT,
    category_name TEXT NOT NULL,
    group_code TEXT NOT NULL,
    date_string TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    coach1_name TEXT,
    coach2_name TEXT,
    present_count INT DEFAULT 0,
    absent_count INT DEFAULT 0,
    present_member_names JSONB DEFAULT '[]'::jsonb,
    absent_member_names JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. Table des émargements / présences (Contrainte anti-doublon absolue)
CREATE TABLE IF NOT EXISTS public.attendances (
    id TEXT PRIMARY KEY, -- Format stable: memberId_YYYY-MM-DD
    member_id BIGINT REFERENCES public.members(id) ON DELETE CASCADE,
    date_string TEXT NOT NULL,
    time_string TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    check_in_method TEXT DEFAULT 'QR_SCAN',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_member_attendance_per_day UNIQUE (member_id, date_string)
);

-- 7. Journal d'audit des synchronisations
CREATE TABLE IF NOT EXISTS public.sync_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id TEXT NOT NULL,
    entity_name TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    operation_type TEXT NOT NULL,
    resolved_by TEXT DEFAULT 'TIMESTAMP_LWW',
    timestamp TIMESTAMPTZ DEFAULT NOW()
);

-- Sécurité Row Level Security (RLS)
ALTER TABLE public.judo_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.coaches ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.scheduled_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.past_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendances ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_audit_log ENABLE ROW LEVEL SECURITY;

-- Politiques RLS avec clé publique anon
CREATE POLICY "Allow authenticated or anon club app read" ON public.members FOR SELECT USING (true);
CREATE POLICY "Allow authenticated or anon club app upsert" ON public.members FOR ALL USING (true);
CREATE POLICY "Allow attendance upsert" ON public.attendances FOR ALL USING (true);
CREATE POLICY "Allow past sessions upsert" ON public.past_sessions FOR ALL USING (true);
CREATE POLICY "Allow schedule upsert" ON public.scheduled_sessions FOR ALL USING (true);
```

---

## 4. Architecture Technique & Flux de Données

```
┌────────────────────────────────────────────────────────────────────────┐
│                        TABLETTE ANDROID (HORS-LIGNE)                   │
│                                                                        │
│   ┌────────────────────────┐         ┌─────────────────────────────┐   │
│   │  Scanner QR / Tatami   │         │  Espace Administrateur      │   │
│   └───────────┬────────────┘         └──────────────┬──────────────┘   │
│               │ Pointage local (<50ms)              │                  │
│               ▼                                     ▼                  │
│   ┌─────────────────────────────────────────────────────────────┐      │
│   │            JudoRepository & Room Database (SQLite)          │      │
│   │   - Table 'members'          - Table 'attendances'          │      │
│   │   - Table 'categories'       - Table 'scheduled_sessions'   │      │
│   │   - Table 'past_sessions'    - Table 'sync_queue'           │      │
│   └─────────────────────────────┬───────────────────────────────┘      │
│                                 │ Enregistrement immédiat              │
│                                 ▼                                      │
│               ┌─────────────────────────────────────┐                  │
│               │      File d'attente (sync_queue)     │                  │
│               │   Statut: PENDING / SYNCED / ERROR  │                  │
│               └─────────────────┬───────────────────┘                  │
└─────────────────────────────────┼──────────────────────────────────────┘
                                  │
                                  │ Déclencheur: Fermeture de session,
                                  │ 22h00 automatique ou bouton manuel
                                  ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        SUPABASE CLOUD (OFFRE GRATUITE)                 │
│                                                                        │
│   ┌──────────────────────────────────┐  ┌──────────────────────────┐   │
│   │  PostgREST API (Requêtes HTTPS)  │  │  Supabase Storage Bucket │   │
│   │  - Upsert par lots idempotents   │  │  - 'athlete-photos'      │   │
│   │  - Aucune connexion Realtime     │  │  - Compression WebP      │   │
│   │  - Quotas réseau maîtrisés (<1%) │  │  - Cache local sur disque│   │
│   └──────────────────────────────────┘  └──────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Gestion des Photos & Compression WebP
- **Compression client** : Les photos prises ou importées sur la tablette sont redimensionnées (max 600x600 px) et compressées au format **WebP** (qualité 80%) sur l'appareil avant stockage local et upload.
- **Économie de bande passante et stockage** : Taille moyenne par photo judoka : ~25 à 40 Ko (au lieu de 3 à 5 Mo).
- **Cache local disque** : Coil charge directement les photos depuis le répertoire cache de l'application (`context.cacheDir/judoka_photos/`), sans re-téléchargement réseau lors des scans.

---

## 6. Plan d'Implémentation Étape par Étape

### Étape 1 : Modèles Room & Extension DAO
- Créer l'entité `SyncQueueEntity` dans Room pour mémoriser les opérations en attente avec leur payload JSON, table cible, horodatage et tentatives.
- Créer l'entité `AuditLogEntity` pour consigner chaque modification locale et synchronisation.
- Mettre à jour `AppDatabase` (version 2 avec migration sécurisée préservant les adhérents et présences existants).

### Étape 2 : Moteur de Synchronisation `SupabaseSyncService`
- Client HTTP léger (OkHttp déjà inclus dans le projet) pour appeler l'API REST Supabase via requêtes `POST/PATCH` avec header `Prefer: resolution=merge-duplicates` (upsert idempotent).
- Gestion de la file d'attente : traitement par lots, confirmation serveur, marquage des réussites et mise en pause sécurisée en cas de coupure Internet.
- Synchronisation descendante : récupération des éventuelles modifications créées sur un autre appareil selon la règle `updated_at > last_sync_time`.

### Étape 3 : Planification Automatique & Gestionnaire de Fermeture
- `DailySyncWorker` ou déclencheur programmé à 22h00 si l'appareil est connecté.
- Composant `QuitSyncDialog` intégré au bouton Quitter / Déconnexion dans la barre d'en-tête admin.

### Étape 4 : Gestion des Sauvegardes & Restauration
- Export complet de la base locale en fichier JSON standard téléchargeable.
- Import et restauration vérifiable : parseur avec validation de structure avant insertion.

### Étape 5 : Tests Unitaires et Validation
- Test unitaire de la file de synchronisation (ajout, idempotence, reprise sur échec).
- Test unitaire de la résolution de conflits par horodatage.
- Test de sauvegarde et de restauration complète des données.
- Test de persistance et de validation de la compilation (`compile_applet`).

---

Ce plan garantit une vitesse instantanée sur le tatami, zéro coût d'hébergement, une sauvegarde cloud fiable et une sécurité complète des données du dojo.
