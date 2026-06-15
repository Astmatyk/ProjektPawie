DROP TRIGGER IF EXISTS trigger_file_upload_guard ON files;
DROP FUNCTION IF EXISTS verify_and_update_storage();
DROP TRIGGER IF EXISTS trigger_file_delete_cleanup ON files;
DROP FUNCTION IF EXISTS reduce_user_storage_on_delete();

DROP TABLE IF EXISTS shared_links;
DROP TABLE IF EXISTS files;
DROP TABLE IF EXISTS folders;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS limit_levels;

CREATE TABLE limit_levels (
    id BIGSERIAL PRIMARY KEY,
    level_name VARCHAR(32) NOT NULL UNIQUE, -- np. 'FREE', 'PREMIUM', 'ENTERPRISE'
    max_bytes BIGINT NOT NULL                -- maksymalna pojemność w bajtach (np. 5368709120 dla 5GB)
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(30) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) DEFAULT 'ROLE_USER' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    -- liczniki
    number_of_files INT DEFAULT 0 NOT NULL,
    total_usage_bytes BIGINT DEFAULT 0 NOT NULL, -- łączna waga wszystkich plików użytkownika
    -- Relacja do pakietu limitów. Domyślnie każdy dostaje pakiet o ID = 1 (czyli 'FREE')
    limit_level_id BIGINT DEFAULT 1 NOT NULL,
    CONSTRAINT fk_users_limit_level FOREIGN KEY (limit_level_id) REFERENCES limit_levels(id)
);

CREATE TABLE folders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    foldername VARCHAR(30) NOT NULL,
    parent_id BIGINT, -- NULL oznacza główny katalog użytkownika (root)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    -- relacje z kaskadowym usuwaniem
    CONSTRAINT fk_folders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_folders_parent FOREIGN KEY (parent_id) REFERENCES folders(id) ON DELETE CASCADE,

    -- w jednym folderze nie może być dwóch tak samo nazwanych podfolderów
    CONSTRAINT uq_user_folder_parent UNIQUE (user_id, parent_id, foldername)
);

CREATE TABLE files (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    folder_id BIGINT, -- NULL oznacza, że plik leży w katalogu głównym (root)
    filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    size BIGINT NOT NULL, -- rozmiar w bajtach
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT fk_files_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_files_folder FOREIGN KEY (folder_id) REFERENCES folders(id) ON DELETE CASCADE,

    -- klucz unikalności dla braku duplikatów plików
    CONSTRAINT uq_user_folder_file UNIQUE NULLS NOT DISTINCT (user_id, folder_id, filename)
);

CREATE TABLE shared_links (
    id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE, -- bezpieczny unikalny token w URL
    expires_at TIMESTAMP, -- jeśli NULL, to link jest bezterminowy
    download_count INT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT fk_shared_links_file FOREIGN KEY (file_id) REFERENCES files(id) ON DELETE CASCADE
);

INSERT INTO limit_levels (level_name, max_bytes) VALUES
('FREE', 524288000),       -- 500 MB na start dla każdego
('PREMIUM', 2147483648),   -- 2 GB dla wspierających
('ADMIN', 1099511627776);  -- 1 TB dla administratorów


CREATE OR REPLACE FUNCTION verify_and_update_storage()
RETURNS TRIGGER AS $$
DECLARE
    v_max_bytes BIGINT;
    v_current_usage BIGINT;
BEGIN
    -- najpierw pobieramy limit bajtów przypisany do poziomu limitu użytkownika
    SELECT l.max_bytes, u.total_usage_bytes
    INTO v_max_bytes, v_current_usage
    FROM users u
    JOIN limit_levels l ON u.limit_level_id = l.id
    WHERE u.id = NEW.user_id;

    -- walidacja sprawdzająca czy nowy plik nie przekroczy limitu użytkownika
    IF (v_current_usage + NEW.size) > v_max_bytes THEN
        -- rzucamy błąd z arbitralnie wybranym kodem kodem SQLSTATE '45000'
        RAISE EXCEPTION 'User storage limit exceeded! Max allowed: % bytes.', v_max_bytes
            USING ERRCODE = '45000';
    END IF;

    -- jeśli wszystko jest OK, aktualizujemy licznik pamięci oraz liczbę plików u użytkownika
    UPDATE users
    SET total_usage_bytes = total_usage_bytes + NEW.size,
        number_of_files = number_of_files + 1
    WHERE id = NEW.user_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- trigger BEFORE żeby zwalidować plik nim w ogóle trafi do bazy
CREATE TRIGGER trigger_file_upload_guard
BEFORE INSERT ON files
FOR EACH ROW
EXECUTE FUNCTION verify_and_update_storage();

-- funkcja dla triggera deinkrementująca liczbę plików i odejmująca rozmiar usuwanego pliku od sumy użytkownika
CREATE OR REPLACE FUNCTION reduce_user_storage_on_delete()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE users
    SET total_usage_bytes = total_usage_bytes - OLD.size,
        number_of_files = number_of_files - 1
    WHERE id = OLD.user_id;

    PERFORM pg_notify('file_deletion_channel', OLD.storage_path);

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

-- AFTER DELETE trigger przy usuwaniu plików
CREATE TRIGGER trigger_file_delete_cleanup
AFTER DELETE ON files
FOR EACH ROW
EXECUTE FUNCTION reduce_user_storage_on_delete();