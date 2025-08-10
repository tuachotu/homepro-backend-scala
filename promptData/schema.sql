-- PostgreSQL Schema for HomePro Backend
-- Run this script to create the necessary tables

-- Connect to your PostgreSQL server first and create the database:
-- CREATE DATABASE homepro;
-- \c homepro;

-- Users table (matches your existing schema)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(500),
    email VARCHAR(150) UNIQUE,
    phone_number VARCHAR(15),
    profile JSONB DEFAULT '{}'::jsonb,
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_modified_by VARCHAR(100),
    last_modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

CREATE INDEX idx_active_users ON users (deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_email ON users (email);
CREATE INDEX idx_firebase_uid ON users (firebase_uid);
CREATE INDEX idx_phone_number ON users (phone_number);

-- Roles table (matches your existing schema)
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

CREATE INDEX idx_roles_deleted_at ON roles (deleted_at);
CREATE INDEX idx_roles_name ON roles (name);

-- User roles junction table (matches your existing schema)
CREATE TABLE user_roles (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    role_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT user_roles_user_id_role_id_key UNIQUE (user_id, role_id)
);

CREATE INDEX idx_user_roles_deleted_at ON user_roles (deleted_at);
CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);
CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);

-- Support requests table
CREATE TABLE support_requests (
    id UUID PRIMARY KEY,
    homeowner_id UUID NOT NULL,
    home_id UUID,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'open',
    priority VARCHAR(50) NOT NULL DEFAULT 'medium',
    assigned_expert_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    FOREIGN KEY (homeowner_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (assigned_expert_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_homeowner_id ON support_requests (homeowner_id);
CREATE INDEX idx_status ON support_requests (status);
CREATE INDEX idx_assigned_expert ON support_requests (assigned_expert_id);
CREATE INDEX idx_created_at ON support_requests (created_at);

-- Insert some default roles
INSERT INTO roles (name, description) VALUES 
('admin', 'System administrator with full access'),
('homeowner', 'Property owner who can create support requests'),
('expert', 'Service expert who can handle support requests'),
('manager', 'Manager who can oversee operations');