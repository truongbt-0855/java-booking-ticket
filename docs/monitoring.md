# Monitoring: Prometheus + Grafana + Node Exporter + mysqld-exporter

## Vai trò từng thành phần

| Thành phần | Vai trò |
|-----------|---------|
| **Node Exporter** | Thu thập metrics từ máy chủ (CPU, RAM, disk, network) và expose ra HTTP endpoint. Không lưu gì, chỉ đọc và trả về khi được hỏi. |
| **mysqld-exporter** | Kết nối vào MySQL, đọc các internal metrics (connections, query stats, InnoDB, replication...) và expose ra HTTP endpoint cho Prometheus scrape. |
| **Prometheus** | Chủ động **scrape** (hỏi) các endpoint metrics theo định kỳ và **lưu lại** dưới dạng time-series database. Là bộ nhớ của hệ thống monitoring. |
| **Grafana** | Đọc data từ Prometheus và vẽ thành dashboard. Không lưu metrics, chỉ query và hiển thị. |

---

## Luồng tương tác

```
Spring Boot App          Node Exporter        mysqld-exporter
/actuator/prometheus      :9100/metrics         :9104/metrics
        ↑                      ↑                      ↑
        └──────────── Prometheus ───────────────────┘
                      (scrape mỗi 5s, lưu vào DB)
                            ↑
                            │ query (PromQL)
                          Grafana
                        (vẽ dashboard)
                            ↑
                          Browser
```

---

## Cấu hình trong project

| Thành phần | URL | Ghi chú |
|-----------|-----|---------|
| Prometheus | http://localhost:9090 | Xem targets tại `/targets` |
| Grafana | http://localhost:3000 | admin / admin1234 |
| Spring Boot metrics | http://localhost:8080/actuator/prometheus | Scrape bởi Prometheus |
| Node Exporter | http://localhost:9100/metrics | Metrics của host machine |
| mysqld-exporter | http://localhost:9104/metrics | Metrics của MySQL |

Config Prometheus: [`environment/prometheus/prometheus.yml`](../environment/prometheus/prometheus.yml)

Config mysqld-exporter: [`environment/mysqld-exporter/.my.cnf`](../environment/mysqld-exporter/.my.cnf)
— Kết nối vào MySQL với user `root`, host `mysql` (tên container), port `3306`.

---

## Workflow thực tế

Không ai ngồi nhìn dashboard cả ngày. Workflow thực tế:

```
Prometheus phát hiện metric vượt ngưỡng
        ↓
Gửi alert → Slack / Email / PagerDuty
        ↓
On-call engineer nhận alert
        ↓
Mở Grafana để điều tra nguyên nhân
```

---

## Chỉ số quan trọng cần quan tâm theo từng service

### Spring Boot App

| Chỉ số | Ngưỡng cần lo | Ý nghĩa |
|--------|--------------|---------|
| **JVM Heap used** | > 80% | Sắp OutOfMemory, cần tăng `-Xmx` hoặc tìm memory leak |
| **GC pause time** | > 500ms | GC chạy quá lâu, app bị lag trong khoảng đó |
| **HTTP error rate** (5xx) | > 1% | Có lỗi nghiêm trọng xảy ra phía server |
| **HTTP response time (p99)** | > 1s | 1% request bị chậm — thường do DB hoặc lock |
| **DB connection pool active** | > 80% | Sắp hết connection, request bị xếp hàng chờ |
| **Thread pool active** | > 80% | Server đang xử lý gần hết capacity |

### MySQL (mysqld-exporter)

| Chỉ số | Metric name | Ngưỡng cần lo | Ý nghĩa |
|--------|------------|--------------|---------|
| **Connections đang dùng** | `mysql_global_status_threads_connected` | > 80% `max_connections` | Sắp hết connection, app sẽ bị lỗi `Too many connections` |
| **Slow queries** | `mysql_global_status_slow_queries` | Tăng liên tục | Query chạy lâu hơn `long_query_time`, cần review index |
| **InnoDB buffer pool hit rate** | `mysql_global_status_innodb_buffer_pool_reads` | Hit rate < 95% | MySQL phải đọc đĩa nhiều thay vì dùng cache trong RAM |
| **Query per second (QPS)** | `mysql_global_status_queries` | Spike bất thường | Traffic tăng đột biến, có thể bị tấn công hoặc bug loop |
| **Aborted connections** | `mysql_global_status_aborted_connects` | > 0 và tăng | App không đóng connection đúng cách, hoặc sai credentials |
| **InnoDB row locks waited** | `mysql_global_status_innodb_row_lock_waits` | > 0 và tăng | Có transaction bị block bởi lock — thường do deadlock hoặc transaction dài |
| **Replication lag** | `mysql_slave_status_seconds_behind_master` | > 10s | Nếu dùng replication: replica đang lag, read từ replica có thể lỗi thời |

### Node Exporter (Host machine)

| Chỉ số | Ngưỡng cần lo | Ý nghĩa |
|--------|--------------|---------|
| **CPU usage** | > 80% liên tục | Server đang bị overload |
| **RAM available** | < 10% | Sắp hết RAM, OS sẽ dùng swap (rất chậm) |
| **Disk I/O wait** | > 20% | Disk đang là bottleneck, MySQL chịu ảnh hưởng nhiều nhất |
| **Network errors** | > 0 | Có vấn đề ở tầng network |

---

## Ai setup & ai dùng

- **DevOps/SRE** — setup Prometheus, Grafana, alert rules, tuning threshold
- **Developer** — nhìn vào khi debug performance issue, không cần quan tâm hàng ngày
- **On-call engineer** — trực hệ thống, xử lý khi có alert

---

## Grafana Dashboard

Project dùng các dashboard sau, import bằng ID:

| Dashboard | ID | Dùng để xem |
|-----------|-----|------------|
| JVM (Micrometer) | `4701` | Spring Boot: heap, GC, threads, HTTP |
| MySQL Overview (mysqld-exporter) | `7362` | MySQL: connections, QPS, slow queries, InnoDB |
| Node Exporter Full | `1860` | Host: CPU, RAM, disk, network |

> Import dashboard: Grafana → **Dashboards → Import → nhập ID → chọn datasource Prometheus**

### Dashboard JVM (Micrometer) — ID `4701`

Lưu ý khi import trên Grafana >= 10 (áp dụng cho tất cả dashboard):
- Variable `DS_PROMETHEUS` không được tự tạo khi import
- Phải tạo thủ công: **Settings → Variables → New variable**
  - Name: `DS_PROMETHEUS`
  - Type: `Data source`
  - Plugin type: `Prometheus`

---

## Reload Prometheus sau khi sửa config

Prometheus chỉ đọc config 1 lần khi khởi động. Sau khi sửa `prometheus.yml` cần reload:

```bash
# Hot reload (không downtime) — yêu cầu --web.enable-lifecycle
curl -X POST http://localhost:9090/-/reload

# Hoặc restart container qua Docker Desktop
```
