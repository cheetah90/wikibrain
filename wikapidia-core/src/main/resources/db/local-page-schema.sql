CREATE TABLE IF NOT EXISTS local_page (
  id BIGINT AUTO_INCREMENT PRIMARY KEY NOT NULL,
  lang_id SMALLINT NOT NULL,
  page_id INT NOT NULL,
  title VARCHAR(256) NOT NULL,
  name_space SMALLINT NOT NULL,
  is_redirect BOOLEAN NOT NULL,
  is_disambig BOOLEAN NOT NULL
);

DROP INDEX IF EXISTS local_page_idx_page_id;
DROP INDEX IF EXISTS local_page_idx_page_title;