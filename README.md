# Astrax Pet RPG 2.0

Plugin pet RPG cho **Paper 1.21.4 / Java 21**. Nâng cấp từ bản 1.2.0 (giữ main class `vn.soulpets.SoulPetsPlugin`, lệnh `/pet`, `/petadmin`, quyền `soulpets.*` vẫn hoạt động).

## Tính năng
| Hệ thống | Mô tả |
|---|---|
| Pet | 42 pet mẫu (6 hệ x 6 độ hiếm + 6 Ancient), thêm không giới hạn trong `pets.yml`. Hệ: Lửa, Băng, Sét, Bóng tối, Ánh sáng, Thiên nhiên (có bảng khắc hệ). |
| Độ hiếm | Common, Uncommon, Rare, Epic, Legendary, Mythic, Ancient |
| Trứng | `eggs.yml`: giá, thời gian ấp, tỉ lệ rơi, lọc hệ, **pity** theo từng độ hiếm. Lò ấp lưu theo thời điểm thật (an toàn khi restart/offline). Pet trùng -> mảnh (shard) để nâng sao. Hiệu ứng title/pháo hoa/âm thanh, broadcast cho độ hiếm cao. |
| Chỉ số | Level, EXP, HP/ATK/DEF/SPEED, điểm kỹ năng (+1 mỗi cấp), kỹ năng chủ động và bị động |
| Tiến hóa / Sao / Trang bị | Nhiều nhánh tiến hóa (cấp, sao, Linh Hồn, nguyên liệu); nâng sao tối đa 5 bằng mảnh; 3 slot trang bị, nâng cấp bằng `upgrade_stone` + Linh Hồn |
| AI chiến đấu | Tank / Healer / DPS, bảo vệ chủ (ưu tiên quái đang đánh chủ), chọn mục tiêu thông minh, cooldown, chống spam, ngân sách xử lý chống lag |
| GUI | Menu chính, bộ sưu tập phân trang, chi tiết pet, trứng/lò ấp, chọn trang bị, **xác nhận** các hành động quan trọng |
| Lưu trữ | SQLite mặc định, MySQL tùy chọn; ghi bất đồng bộ bằng transaction; xử lý người chơi offline |
| Hook | PlaceholderAPI, MythicMobs (reflection), MythicLib (reflection, thử nghiệm) |

## Cài đặt
1. Cần Paper 1.21.4, Java 21.
2. Build: `mvn clean package` -> `target/AstraxPetRPG-2.0.0.jar`. Kiểm tra JAR: `./scripts_verify_jar.sh`.
3. Chép JAR vào `plugins/`, khởi động server. **Lần đầu server cần internet** để Paper tải `sqlite-jdbc` và `mysql-connector-j` (khai báo `libraries:` trong plugin.yml).
4. Chỉnh `plugins/AstraxPetRPG/*.yml`, rồi `/petadmin reload`.

## Lệnh và quyền
- `/pet` (menu), `/pet collection|eggs|summon|dismiss|info|souls|skill [id]|rename <tên>` - quyền `astraxpet.use` (mặc định true)
- `/petadmin reload|debug|list|give <p> <petId>|egg <p> <eggId> <n>|gear <p> <id> <n>|material <p> <id> <n>|souls <p> <n>|xp <p> <n>` - quyền `astraxpet.admin`
- Chuột phải vào pet của mình để mở menu.
- Người chơi offline: admin vẫn cấp được (đọc-sửa-ghi tuần tự trên thread IO), tên phải nằm trong cache server.

## Cấu hình
- `config.yml`: database, kinh tế, hiệu ứng, chiến đấu, buff, tỉ lệ rơi nguyên liệu, hook.
- `pets.yml`: mỗi pet: `name, element, rarity, role, entity, icon, base/growth{hp,atk,def,speed}, skills, evolutions, mythic-mob`. Có thể sinh lại danh sách mẫu bằng `python3 tools/gen_pets.py`.
- `skills.yml`, `eggs.yml`, `equipment.yml`: xem chú thích đầu mỗi file.
- Công thức chỉ số: `(base + growth*(lv-1)) * hệ số độ hiếm * (1 + 0.1*sao) + trang bị`, rồi nhân % kỹ năng bị động.
- Pity: `pity: {EPIC: 50}` nghĩa là sau 49 lần ấp không ra Epic+ thì lần thứ 50 chắc chắn ra Epic+ (bộ đếm lưu trong DB).

## PlaceholderAPI
`%astraxpet_souls%`, `%astraxpet_count%`, `%astraxpet_active_name%`, `_level`, `_rarity`, `_element`, `_role`, `_star`, `_hp`, `_atk`, `_def`, `_speed`.

## MythicMobs
- Ví dụ: `src/main/resources/mythicmobs/example-pet-mob.yml` và `example-pet-skills.yml` (chép vào thư mục tương ứng của MythicMobs).
- Dùng mob Mythic làm hình dạng pet: thêm `mythic-mob: AstraxEmberDrake` vào pet trong `pets.yml`.
- Hiệu ứng skill Mythic: thêm `mythic-skill: AstraxFlameBurst` vào skill trong `skills.yml`. Sát thương do plugin tính; Mythic chỉ chạy hiệu ứng.
- Nếu API Mythic khác phiên bản, plugin ghi cảnh báo và tự dùng entity vanilla.

## MythicLib
Nếu có MythicLib, buff ATK/DEF/HP của pet cộng vào stat MythicLib (`ATTACK_DAMAGE`, `DEFENSE`, `MAX_HEALTH`) thay vì attribute vanilla. Tính năng thử nghiệm qua reflection; lỗi sẽ tự tắt (xem log). Tắt bằng `mythiclib.enabled: false`.

## Thiết kế chống lỗi
- **Một task AI duy nhất**, round-robin, tối đa `combat.max-pets-per-tick` pet mỗi lượt; hủy khi `onDisable`/reload (không trùng task).
- **Mỗi chủ tối đa 1 entity**: `PetManager` hủy entity cũ trước khi spawn; pet `setPersistent(false)`, bị dọn khi quit, chunk unload (`EntitiesUnloadEvent`), `EntitiesLoadEvent` và lúc bật plugin.
- Pet không bao giờ tự đánh (mọi sát thương vanilla từ pet bị hủy); sát thương do AI plugin tính -> kiểm soát được.
- Các map theo người chơi/pet (cooldown, rate limiter, threat) được dọn khi quit/thu hồi; `RateLimiter.purge` chạy định kỳ.
- **Không mất dữ liệu**: nạp dữ liệu ở `AsyncPlayerPreLoginEvent`, nếu lỗi đọc thì từ chối vào server (không tạo profile rỗng đè lên dữ liệu cũ); ghi bằng bản snapshot, transaction, một thread IO tuần tự; `onDisable` chờ ghi xong; ghi lỗi sẽ đánh dấu dirty để autosave thử lại.

## Debug
- `debug: true` trong `config.yml` để log cast kỹ năng.
- `/petadmin debug`: số pet đang hoạt động, số pet và thời gian AI lượt trước, trạng thái hook.
- Pet không xuất hiện: kiểm tra `entity:` trong `pets.yml` là `EntityType` hợp lệ; log sẽ báo "Không spawn được ...".
- "Dữ liệu pet đang lỗi" khi vào server: xem log `SEVERE` của `AstraxPetRPG` (DB không mở được / MySQL sai thông tin).
- Mob thù địch (Warden, Enderman...) dùng làm pet chỉ là hình dạng: vô hại, không tự nhắm mục tiêu, không phá block.

## Unit test
`mvn test` chạy JUnit 5: `CoreSystemsTest` (rarity, khắc hệ, pity, ấp trứng, level/kỹ năng, chỉ số, tiến hóa nhiều nhánh, nâng sao, trang bị, chọn mục tiêu, cooldown/chống spam, deep copy) và `SqlStorageTest` (SQLite round-trip, offline modify).

## Cấu trúc
`vn.soulpets.{pet, rarity, egg, evolution, combat, skills, equipment, mythicmobs, mythiclib, storage, gui, commands, config, hook}`; `plugin.yml` ở `src/main/resources/plugin.yml`.

## Di chuyển từ 1.2.0
Dữ liệu cũ (`pets.db`, bảng `pet_players/pet_collection`) **không** được tự động chuyển sang schema mới (`ap_*`). Pet cũ theo loại vanilla không còn tương ứng 1-1; cần script chuyển nếu muốn giữ.
