CREATE INDEX IF NOT EXISTS META_IDX_COMP ON META_INFO(COMPONENT);
CREATE INDEX IF NOT EXISTS META_IDX_COMP_LANG ON META_INFO(COMPONENT, LANG_ID)