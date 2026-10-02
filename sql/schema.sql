-- CampusFind AI: schema exported from the verified MySQL installation.
CREATE DATABASE IF NOT EXISTS campus_lost_found CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_lost_found;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `announcements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `message` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_action` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `actor_id` bigint DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `ip` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resource_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `event_result` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campus_locations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9w3u2q1uwbyvqdw5g4u5mghf7` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt8o6pivur7nn124jehx7cygw5` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `claims` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `answers` text COLLATE utf8mb4_unicode_ci,
  `claimant_confirmed` bit(1) NOT NULL,
  `code_attempts` int NOT NULL,
  `code_expires_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `finder_confirmed` bit(1) NOT NULL,
  `finder_response` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `handover_code` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `handover_hash` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `handover_location` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `loss_location` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `returned_at` datetime(6) DEFAULT NULL,
  `review_note` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `score` int NOT NULL,
  `serial` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `claimant_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `lost_item_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDXq6dj02am5nttov8r4fys2rv96` (`claimant_id`),
  KEY `IDXe8u0i6169x86d2oehyd1jemxr` (`item_id`),
  KEY `IDXhxsqxo7an3f0hsymytw7jrfdq` (`status`),
  KEY `FK2xn3xoy8fqyxqafyw6lb3xhfx` (`lost_item_id`),
  CONSTRAINT `FK2xiinn2gb4ynk61d94ub53rg7` FOREIGN KEY (`claimant_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `FK2xn3xoy8fqyxqafyw6lb3xhfx` FOREIGN KEY (`lost_item_id`) REFERENCES `items` (`id`),
  CONSTRAINT `FKn32noe83jn3fnor7ybgtwmvwc` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_matches` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `score` int NOT NULL,
  `found_item_id` bigint NOT NULL,
  `lost_item_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2osejsfap4dcirx7s6hy2v2jh` (`lost_item_id`,`found_item_id`),
  KEY `FKqroi30jf6ibn93md0a6tk5uy3` (`found_item_id`),
  CONSTRAINT `FKcoh1j7ah8vvw12shb3fpamuxd` FOREIGN KEY (`lost_item_id`) REFERENCES `items` (`id`),
  CONSTRAINT `FKqroi30jf6ibn93md0a6tk5uy3` FOREIGN KEY (`found_item_id`) REFERENCES `items` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `brand` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `building` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `color` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `event_date` date DEFAULT NULL,
  `description` varchar(2500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `building_floor` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_seen` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `private_details` text COLLATE utf8mb4_unicode_ci,
  `room` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `serial` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_location` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `event_time` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(140) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `approximate_value` decimal(12,2) DEFAULT NULL,
  `owner_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDXru1jx0vxlk5ep9dk2m4j6242d` (`type`,`status`),
  KEY `IDX56ucaca0dpa6tdh3aqp0ioh0x` (`category`),
  KEY `IDXqwj3kj9a8x1uakxvrvu6tf1mq` (`owner_id`),
  CONSTRAINT `FKglmo9boohmp35bc0e3es6kneq` FOREIGN KEY (`owner_id`) REFERENCES `user_accounts` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `message` varchar(1500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_read` bit(1) DEFAULT NULL,
  `title` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK37q8khxebgd9lb12yhyesjhdf` (`user_id`),
  CONSTRAINT `FK37q8khxebgd9lb12yhyesjhdf` FOREIGN KEY (`user_id`) REFERENCES `user_accounts` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `uploads` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `claim_id` bigint DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `item_id` bigint DEFAULT NULL,
  `mime_type` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `original_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_path` varchar(600) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `purpose` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `owner_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDXppx4kbp5fjbfubgx7piro7ent` (`item_id`),
  KEY `IDXe2ualinkystyjgyt3wh1p2bxw` (`claim_id`),
  KEY `FK5lh1n0qv6shr300b0el6r7p1k` (`owner_id`),
  CONSTRAINT `FK5lh1n0qv6shr300b0el6r7p1k` FOREIGN KEY (`owner_id`) REFERENCES `user_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_accounts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `auth_version` int NOT NULL,
  `college_id` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `department` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email_verified` bit(1) NOT NULL,
  `failed_attempts` int NOT NULL,
  `flagged` bit(1) NOT NULL,
  `identity_verified` bit(1) NOT NULL,
  `locked_until` datetime(6) DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `profile_image_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reputation` int NOT NULL,
  `reset_expires_at` datetime(6) DEFAULT NULL,
  `reset_token_hash` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` varchar(15) COLLATE utf8mb4_unicode_ci NOT NULL,
  `verification_expires_at` datetime(6) DEFAULT NULL,
  `verification_token_hash` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `verified` bit(1) NOT NULL,
  `study_year` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKf9sl209luxhu4rylls0h1m625` (`email`),
  UNIQUE KEY `UK18dm6dch5sc5alqxfqi1sd10g` (`college_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

