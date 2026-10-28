# Comic Net — Phase 0 Swing-first baseline (revision3)

Trạng thái thực thi hiện tại: [implementation-status.md](implementation-status.md).
Nội dung dưới đây là snapshot thiết kế Phase 0; Phase 0/1 đã được người dùng nghiệm thu,
Phase 2 đang triển khai/QA. Không dùng nhãn Not Started lịch sử bên dưới làm trạng thái source hiện tại.

Ngày lập: 2026-10-27 (Asia/Saigon). Trạng thái thiết kế: **Phase 0 đã được người dùng chấp thuận**; tài liệu lưu cả lịch sử baseline. Trạng thái thực thi P1/P2 theo implementation-status.md.

Nguồn chính thức: [prj_app.md](../prj_app.md), V2.1 Swing-first theo yêu cầu user2026-10-27. JavaSwing là UI chính/MVP NetBeans, React/TypeScript là Web extension giữ sau MVP. Java21/Boot4.1.2/JUnit6/Maven/SQL2025/23module/243features giữ nguyên; source UI/run/design/testing/roadmap đã cập nhật, không còn giới hạn delta chỉ hai dòng Boot/JUnit của revision trước. Application source/POM/migration P1–P2 đã có; các ghi chú chưa triển khai trong snapshot P0 là lịch sử.

## Bộ tài liệu

| Tài liệu | Mục đích |
|---|---|
| [Scope & roadmap](requirements/scope-roadmap.md) | MVP, 23 epic, các phase và Definition of Done |
| [Feature register](requirements/feature-register.md) | Mỗi bullet chức năng trong 23 module được giữ nguyên, gán ID và phase |
| [SRS Swing MVP](requirements/srs.md) | Constraints, actors, functional/NFR và platform coverage của23module |
| [Swing impact](architecture/swing-impact.md) | Phân tích delta, quyết định user, NetBeans/Maven và scope Web |
| [MVP backlog](requirements/mvp-backlog.md) | Story, acceptance criteria, dependency và test traceability |
| [MVP traceability](requirements/mvp-traceability.md) | 74 feature có phần MVP →19 stories → API/tables/test families |
| [Architecture](architecture/system-design.md) | Ownership, dependency DAG, giao tiếp và cây thư mục |
| [Permissions & security](security/permission-matrix.md) | 9 actor/role, phạm vi dữ liệu, token và security boundaries |
| [Security Foundation](security/foundation.md) | Allowlist/CORS/CSRF/key/password config, ranh giới P1/P2 và SEC-P1 acceptance |
| [Core ERD](database/core-erd.md) | Mô hình logic, cardinality, SQL Server types/constraints/indexes |
| [Business workflows](requirements/business-workflows.md) | State machines, 15 workflow, transaction và exception flows |
| [MVP API guide](api/mvp-contract.md) | Quy ước, concurrency, uploads, DTO và quyền của từng operation |
| [OpenAPI](api/openapi-mvp.json) | Hợp đồng máy đọc được; có test đối chiếu controller |
| [UI audit](design/ui-audit.md) | Bằng chứng Figma, screen inventory, phần thiếu và responsive |
| [Swing screen map](design/swing-screen-map.md) | SW00..18 phủ mọi MVP story, desktop layouts/states/threading/API |
| [Testing & NFR](testing/strategy.md) | Tiêu chí đo, test matrix, release gates |
| [Phase 2 acceptance](testing/phase-2-acceptance.md) | PASS/FAIL/BLOCKED, evidence và hướng dẫn kiểm thử thủ công |
| [Risks & decisions](architecture/risks-decisions.md) | Mức ưu tiên, decision log, blockers và câu hỏi còn mở |
| [Phase 1 plan](deployment/phase-1-plan.md) | File dự kiến, thứ tự công việc, kiểm tra môi trường và gate |
| [Environment baseline](deployment/environment-baseline.md) | Version compatibility, user-approved config, metadata/runtime evidence và giới hạn |
| [Validation](validation.md) | Kết quả kiểm tra tài liệu, giới hạn kiểm chứng và readiness |

## Phân biệt trạng thái

- **Requirement:** yêu cầu trong tài liệu gốc, phải bảo toàn.
- **Proposed baseline:** phương án thiết kế cụ thể để review; không tự coi là quyết định sản phẩm đã được người dùng duyệt.
- **Deferred implementation:** giữ trong backlog và có phase, không phải loại khỏi scope.
- **Verified evidence:** chỉ dùng với file/tool/test thực tế đã kiểm tra; không dùng cho API/DB/UI chưa xây dựng.

Tại thời điểm chốt Phase 0, 23 module và Phase 1 chưa được triển khai. Hiện Phase 1 đã được nghiệm thu, Phase 2 đang kiểm thử; dùng [biên bản Phase 2](testing/phase-2-acceptance.md) để xem trạng thái thực tế.

## Gate Phase 0

1. 23 module và từng bullet chức năng có phase, owner và traceability.
2. MVP có permission, dữ liệu, API và acceptance tests; dependency không có chu trình thiết kế.
3. ERD bảo toàn Chapter/Page identity qua version; transaction tài chính và sync có phương án rõ.
4. Foundation/Swing-first và wizard/ops policy đã có user decision; D02–D05/D08 còn review trước domain P2, finance trước P4. Không yêu cầu chốt mọi policy tương lai để scaffold P1.
5. Figma là reference; desktop layout/screens/states có inventory proposal, không frame Swing đã duyệt hoặc UI source. React retained ngoài MVP, không pixel-comparison/mobile/Vite gate cho Swing.
6. Phase 1 chỉ nhận scope đã xác nhận, không tự mở rộng sang tính năng Phase 2.

Các tài liệu đầy đủ về mặt bản nháp không đồng nghĩa Phase 0 đã được nghiệm thu.

P1 mục tiêu: root Maven parent+backend/Swing children, hai NetBeans mains và Swing ping thật, không code nghiệp vụ/Web. P2:19stories và74feature phần MVP usable Swing;91API operations design gồm setup capability, không application đã chạy. Backup/restore user đã giữ operator procedure + Swing runbook/log viewer. Không bắt đầu P1 trước xác nhận riêng.
