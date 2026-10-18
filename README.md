# Astrax Pet RPG 2.0

Plugin pet RPG cho **Paper 1.21.4** (Java 21): 54 pet 6 hệ nguyên tố, 7 độ hiếm, trứng + ấp + pity, level/EXP/chỉ số, kỹ năng chủ động & bị động,
tiến hóa nhiều nhánh, nâng sao, trang bị, AI chiến đấu (Tank/Healer/DPS), dungeon + boss, nhiệm vụ ngày/tuần, thành tựu, PetDex, bảng xếp hạng,
GUI phân trang, PlaceholderAPI, SQLite/MySQL.

> **Trạng thái kiểm thử (trung thực):** toàn bộ logic thuần (gacha/pity, EXP, chỉ số, khắc hệ, tiến hóa, nhiệm vụ, mã hóa dữ liệu, chọn mục tiêu, cooldown...)
> đã được biên dịch và chạy test thật. Phần gọi Bukkit/Paper (AI pet, GUI, lệnh, dungeon, SQL) được viết theo Paper API 1.21.4 nhưng **chưa chạy trên server thật** —
> hãy thử trên server test trước khi đưa lên server chính (xem mục *Debug*).

## 1. Cài đặt
1. Yêu cầu: Java 21, Paper 1.21.4. (Tùy chọn: PlaceholderAPI, MythicMobs, MythicLib.)
2. Build: `mvn clean package` → `target/AstraxPetRPG-2.0.0.jar` (Maven tự chạy unit test).
3. Kiểm tra JAR: `sh tools/verify-jar.sh target/AstraxPetRPG-2.0.0.jar` (xác nhận `plugin.yml` ở root, class main, các module và file config).
4. Chép JAR vào `plugins/`, khởi động server. Lần đầu Paper sẽ tải driver SQLite/MySQL (khai báo `libraries:` trong plugin.yml) nên cần mạng.
5. Các file cấu hình được tạo trong `plugins/AstraxPetRPG/`: `config.yml, pets.yml, skills.yml, eggs.yml, equipment.yml, quests.yml, achievements.yml, dungeons.yml`.

## 2. Lệnh
| Lệnh | Tác dụng |
|---|---|
| `/pet` / `/pet menu` | Menu chính |
| `/pet summon` · `/pet dismiss` | Triệu hồi / thu hồi pet đang chọn |
| `/pet stats` | Chỉ số chi tiết |
| `/pet skill` [`list`/`cast <id>`/`upgrade <id>`] | Menu kỹ năng / dùng kỹ năng / nâng cấp |
| `/pet evolve [nhánh]` | Tiến hóa (menu nếu không ghi nhánh) |
| `/pet equip` [`hand`/`upgrade <ô>`/`unequip <ô>`] | Trang bị (ô: WEAPON, ARMOR, CHARM) |
| `/pet egg` · `/pet gacha [trứng]` | Trứng & lò ấp · mở trứng ngay (tốn gấp `instant-hatch-cost-multiplier`) |
| `/pet dex` · `/pet quests` · `/pet top` | PetDex · nhiệm vụ · xếp hạng |
| `/pet dungeon [start <id>/leave]` | Dungeon |
| `/pet rename <tên>` · `/pet release [confirm]` · `/pet select <số>` | Đổi tên · thả pet · chọn pet |
| `/petadmin give pet\|egg\|souls\|exp\|equip\|shards <người> <id/số> [n]` | Tặng (người offline vẫn được, trừ exp/equip) |
| `/petadmin set level <người> <cấp>` · `reset <người> confirm` · `info <người>` | Quản trị |
| `/petadmin reload` · `save` · `list` · `setdungeon <id>` · `dungeon stop <người>` · `debug [on/off/dump]` | Vận hành |

Quyền: `soulpets.use` (mặc định true), `soulpets.dungeon` (true), `soulpets.admin` (op).
Click chuột phải vào pet đang triệu hồi để mở menu.

## 3. Thêm nội dung (không giới hạn)
* **Pet mới**: thêm khối vào `pets.yml` (id, name, element, rarity, role, entity, stats, skills, evolutions), rồi `/petadmin reload`. Plugin cảnh báo trong console nếu skill/nhánh tiến hóa sai id.
  `obtainable: false` = chỉ có qua tiến hóa. Pet mới tự vào pool trứng theo độ hiếm (và hệ cho trứng nguyên tố).
* **Kỹ năng**: `skills.yml` (hiệu ứng ACTIVE: `DAMAGE_AOE, DAMAGE_SINGLE, CHAIN, HEAL, SHIELD, BUFF`; PASSIVE: `HP_PCT, ATK_PCT, DEF_PCT, SPEED_PCT, CRIT, LIFESTEAL, REGEN, REDUCTION`).
* **Trứng**: `eggs.yml` (tỉ lệ `weights` tùy ý, lọc `element`). **Pity** + **tỉ lệ rơi trứng từ quái**: `config.yml` → `egg.pity`, `egg.drops`.
* **Trang bị / nhiệm vụ / thành tựu / dungeon**: `equipment.yml`, `quests.yml`, `achievements.yml`, `dungeons.yml`.

## 4. Cơ chế chính
* **Độ hiếm**: Common → Uncommon → Rare → Epic → Legendary → Mythic → Ancient (hệ số chỉ số 1.0 … 3.4).
* **Hệ**: Lửa > Băng > Sét > Thiên nhiên > Lửa (x1.3 / x0.8); Ánh sáng ↔ Bóng tối khắc nhau (x1.3). Quái vanilla được gán hệ trong `MobElements`.
* **Pity**: chắc chắn ≥ Epic sau 10 lần, tỉ lệ Legendary+ tăng từ lần 60, chắc chắn ≥ Legendary ở lần 90 (đổi trong config). Trứng không có Epic+ không tính pity.
* **Trùng pet**: đổi thành *mảnh* (dùng nâng sao: 5/10/15/20/25 mảnh) + EXP.
* **Tiến hóa**: mỗi pet gốc có 2 nhánh (Vương = sát thương, Thần = phòng thủ/hỗ trợ), cần cấp, Linh Hồn, nguyên liệu trong túi; tăng cấp tối đa +25 và +20% chỉ số, +2 điểm kỹ năng.
* **AI**: một task duy nhất cho mọi pet (mặc định 0.5s). Ưu tiên kẻ đang đánh chủ → boss → máu thấp (DPS) → gần. Tank kéo aggro (tối đa 3 quái), Healer tự hồi máu khi chủ <65% máu, DPS dùng kỹ năng sát thương. Có sticky-target, giới hạn quét, ngân sách hiệu ứng, cooldown kỹ năng, chống spam lệnh/click.
* **Chống lag / lỗi thực thể**: pet `persistent=false` (không lưu vào chunk), có PDC đánh dấu; tự hồi sinh khi chunk unload (giới hạn số lần), xóa pet mồ côi khi chunk load / khởi động / tắt plugin; buff thuộc tính của chủ luôn được gỡ khi thoát/tắt.
* **Dữ liệu an toàn**: lưu nguyên tử trong transaction (SQLite WAL + synchronous=FULL), ghi qua một luồng IO duy nhất bằng snapshot, thử lại 3 lần, lưu đồng bộ khi tắt server, đọc lại sau khi ghi xong (không đọc dữ liệu cũ khi vào lại nhanh). **Tải lỗi ⇒ từ chối đăng nhập** thay vì tạo hồ sơ trống ghi đè. Người offline được sửa qua `/petadmin`.
* **Kill từ spawner** không cho Linh Hồn/EXP/trứng (`combat.ignore-spawner-mobs`).

## 5. MySQL
`config.yml`: `database.type: mysql` và điền `database.mysql.*`. Bảng `astrax_profiles` được tạo tự động. Chuyển từ SQLite sang MySQL: dừng server, đổi type (dữ liệu SQLite không tự chuyển).

## 6. PlaceholderAPI
`%astrax_souls%`, `%astrax_pets%`, `%astrax_dex%`, `%astrax_dex_total%`, `%astrax_score%`, `%astrax_pet_name%`, `%astrax_pet_level%`, `%astrax_pet_rarity%`, `%astrax_pet_element%`, `%astrax_pet_role%`, `%astrax_pet_power%`, `%astrax_pet_star%`.

## 7. MythicMobs / MythicLib
* **MythicMobs** (reflection, không bắt buộc): `mythic-mob` trong `pets.yml` (hình dạng pet), `mythic` trong `dungeons.yml` (quái/boss), `mythic-skill` trong `skills.yml` (cast skill Mythic khi pet dùng kỹ năng). Ví dụ trong `examples/mythicmobs/`.
* **MythicLib** (thử nghiệm): đọc chỉ số tùy chỉnh `ASTRAX_PET_DAMAGE` (đơn vị %) của chủ và nhân vào sát thương pet. Lỗi/thiếu chỉ số ⇒ bỏ qua.
  Tắt từng hook bằng `hooks.*` trong `config.yml`.

## 8. Debug
1. `/petadmin debug on` — log chi tiết (tải hồ sơ, spawn pet, dùng kỹ năng). `/petadmin debug dump` — liệt kê pet đang hoạt động, entity còn hợp lệ, mục tiêu.
2. Pet không xuất hiện: kiểm tra console có cảnh báo `EntityType không hợp lệ` / `cần entity dạng Mob`; thử `entity: WOLF`.
3. Pet lag: tăng `combat.think-interval-ticks`, giảm `combat.max-scan-entities`, `combat.max-effects-per-tick`.
4. Không kết nối DB: plugin tự tắt để bảo vệ dữ liệu; xem lỗi đầu tiên trong console (driver / quyền / đường dẫn).
5. Mất dữ liệu nghi ngờ: tìm dòng `KHÔNG LƯU ĐƯỢC hồ sơ` trong log (có kèm dữ liệu thô để khôi phục).
6. Test: `mvn test`. JAR: `sh tools/verify-jar.sh`.

## 9. Cấu trúc mã
`pet` (mô hình, PetManager/PetService), `rarity`, `egg` (gacha/pity/ấp), `evolution`, `combat` (chọn mục tiêu, sát thương, cooldown, listener), `skills`, `equipment`, `quest`, `reward`, `dungeon`,
`mythicmobs`, `mythiclib`, `storage` (SQL, ProfileManager, Codec), `gui`, `commands`, `config`, `placeholder`, `util`.
