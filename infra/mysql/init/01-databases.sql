-- Runs once, when the data volume is first created. MYSQL_DATABASE already made flow_demo for the example backend;
-- flow_it is the live-test database, emptied and rebuilt by Flyway before every test.
CREATE DATABASE IF NOT EXISTS flow_it CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON flow_it.* TO 'flow'@'%';
