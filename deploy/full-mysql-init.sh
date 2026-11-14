#!/bin/sh
# Official MySQL entrypoint runs only against an uninitialized NEW volume.
set -eu
root=$(cat /run/secrets/mysql-root-password)
center=$(cat /run/secrets/center-db-password)
course=$(cat /run/secrets/course-db-password)
# All values are generated hex, rejecting SQL metacharacters before substitution.
for value in "$root" "$center" "$course"; do
 case "$value" in ''|*[!0-9a-f]*) echo 'Invalid generated secret' >&2; exit 1;; esac
done
MYSQL_PWD="$root" mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS rbac_center CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS rbac_course CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'rbac_center'@'%' IDENTIFIED BY '$center';
CREATE USER 'rbac_course'@'%' IDENTIFIED BY '$course';
GRANT ALL PRIVILEGES ON rbac_center.* TO 'rbac_center'@'%';
GRANT ALL PRIVILEGES ON rbac_course.* TO 'rbac_course'@'%';
SQL
