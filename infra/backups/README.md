# Backups

## Reader and Goal

This note is for the operator responsible for VNShop environment backups. After reading it, they should be able to find backup output, understand what each file contains, and apply the retention rule without deleting data that may still be needed for rollback.

## What Gets Stored Here

`backup.sh` writes two artifacts per run:

1. `vnshop-YYYYMMDD-HHMMSS.sql.gz`, a compressed PostgreSQL dump for the `vnshop` database.
2. `keycloak-YYYYMMDD-HHMMSS.json`, a Keycloak `vnshop` realm export.

Keep both files from the same timestamp together. Database state and realm configuration need to match when restoring an environment.

## Retention Rule

Use this retention policy unless an incident commander or migration owner asks for a longer hold:

1. Daily backups, keep 7 days.
2. Weekly backups, keep 4 weeks.
3. Monthly backups, keep 12 months.

Never delete a backup that is still inside a migration monitoring window, incident review, audit hold, or manual rollback plan.

## Restore Safety

Restores replace live database contents with data from the selected dump. Confirm the target environment before running restore. Run restore only during an approved recovery or staging validation window.
