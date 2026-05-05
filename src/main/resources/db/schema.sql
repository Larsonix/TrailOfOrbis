-- TrailOfOrbis Player Data Schema
-- Compatible with H2 (MODE=MySQL), MySQL 8.x, PostgreSQL 13+

CREATE TABLE IF NOT EXISTS rpg_players (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    fire INT NOT NULL DEFAULT 0,
    water INT NOT NULL DEFAULT 0,
    lightning INT NOT NULL DEFAULT 0,
    earth INT NOT NULL DEFAULT 0,
    wind INT NOT NULL DEFAULT 0,
    void_attr INT NOT NULL DEFAULT 0,
    unallocated_points INT NOT NULL DEFAULT 0,
    attribute_refund_points INT NOT NULL DEFAULT 10,
    attribute_respecs INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for username lookups (name changes, admin commands)
CREATE INDEX IF NOT EXISTS idx_rpg_players_username ON rpg_players(username);

-- Skill Tree Allocations
CREATE TABLE IF NOT EXISTS rpg_skill_tree (
    uuid VARCHAR(36) PRIMARY KEY,
    allocated_nodes TEXT,              -- JSON array of node IDs
    skill_points INT DEFAULT 0,
    total_points_earned INT DEFAULT 0,
    respecs INT DEFAULT 0,
    skill_refund_points INT DEFAULT 10,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (uuid) REFERENCES rpg_players(uuid) ON DELETE CASCADE
);

-- Index for last modified (useful for cleanup queries)
CREATE INDEX IF NOT EXISTS idx_rpg_skill_tree_last_modified
    ON rpg_skill_tree(last_modified);

-- Player Leveling Data
CREATE TABLE IF NOT EXISTS rpg_levels (
    uuid VARCHAR(36) PRIMARY KEY,
    xp BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (uuid) REFERENCES rpg_players(uuid) ON DELETE CASCADE
);

-- Index for XP leaderboards and queries
CREATE INDEX IF NOT EXISTS idx_rpg_levels_xp ON rpg_levels(xp DESC);

-- Item Registry Cache (for custom RPG gear items)
-- Stores custom item ID → base item ID mappings for server restart persistence
CREATE TABLE IF NOT EXISTS rpg_item_registry (
    custom_id VARCHAR(64) PRIMARY KEY,              -- e.g., "rpg_gear_1706123456789_42"
    base_item_id VARCHAR(128) NOT NULL,             -- e.g., "Weapon_Axe_Copper"
    secondary_interaction_id VARCHAR(128),          -- e.g., "RPG_Stone_Secondary" (nullable for regular gear)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for cleanup queries (remove items not seen in X days)
CREATE INDEX IF NOT EXISTS idx_rpg_item_registry_last_seen ON rpg_item_registry(last_seen);

-- Spawn Gateway State (tracks which worlds have had gateways placed)
-- Persists gateway placement so portals aren't re-created on server restart
CREATE TABLE IF NOT EXISTS rpg_spawn_gateways (
    world_uuid VARCHAR(36) PRIMARY KEY,
    gateways_placed BOOLEAN NOT NULL DEFAULT FALSE,
    portal_count INT NOT NULL DEFAULT 0,
    ring_radius INT NOT NULL DEFAULT 20,
    placed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for querying unplaced worlds (rarely used, but good for debugging)
CREATE INDEX IF NOT EXISTS idx_rpg_spawn_gateways_placed ON rpg_spawn_gateways(gateways_placed);

-- Gateway Upgrade Tiers (per-block tier for Portal_Device gateways)
-- Tracks the upgrade level of each gateway portal in the world
CREATE TABLE IF NOT EXISTS rpg_gateway_tiers (
    world_uuid VARCHAR(36) NOT NULL,
    block_x INT NOT NULL,
    block_y INT NOT NULL,
    block_z INT NOT NULL,
    tier INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (world_uuid, block_x, block_y, block_z)
);

-- Index for loading all gateways in a world
CREATE INDEX IF NOT EXISTS idx_rpg_gateway_tiers_world ON rpg_gateway_tiers(world_uuid);

-- Player Guide Milestones (tracks which guide popups have been shown)
CREATE TABLE IF NOT EXISTS rpg_guide_milestones (
    player_uuid VARCHAR(36) NOT NULL,
    milestone_id VARCHAR(64) NOT NULL,
    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, milestone_id),
    FOREIGN KEY (player_uuid) REFERENCES rpg_players(uuid) ON DELETE CASCADE
);

-- Loot Filter Data (per-player JSON blob)
CREATE TABLE IF NOT EXISTS rpg_loot_filters (
    uuid VARCHAR(36) PRIMARY KEY,
    filter_data TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (uuid) REFERENCES rpg_players(uuid) ON DELETE CASCADE
);
