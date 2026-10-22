FROM postgres:16.15
RUN apt-get update && apt-get install -y --no-install-recommends cron tar age rclone && rm -rf /var/lib/apt/lists/*
COPY backup.sh restore.sh verify-restore.sh offsite.sh /usr/local/bin/
COPY crontab /etc/cron.d/giapha-backup
RUN chmod +x /usr/local/bin/*.sh && chmod 0644 /etc/cron.d/giapha-backup
# cron không thừa hưởng biến môi trường: ghi ra tệp (quyền 600, giá trị được quote) rồi nạp trong tác vụ
ENTRYPOINT ["/bin/bash", "-c", "umask 077; printenv | grep -E '^(PG|RETENTION|BACKUP|SKIP_PHOTOS|MINIO_DATA|OFFSITE|RCLONE)' | while IFS='=' read -r k v; do printf 'export %s=%q\\n' \"$k\" \"$v\"; done > /etc/backup.env && exec cron -f"]
