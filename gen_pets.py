# -*- coding: utf-8 -*-
"""Sinh src/main/resources/pets.yml: 6 hệ x 6 độ hiếm + 6 pet Ancient (tiến hóa nhiều nhánh). Chạy: python3 tools/gen_pets.py"""
els = [("fire", "FIRE"), ("ice", "ICE"), ("lightning", "LIGHTNING"), ("dark", "DARK"), ("light", "LIGHT"), ("nature", "NATURE")]
ents = {"fire": ["FOX", "BLAZE", "MAGMA_CUBE", "STRIDER", "PIGLIN", "HOGLIN"],
        "ice": ["POLAR_BEAR", "SNOW_GOLEM", "STRAY", "GOAT", "WOLF", "RABBIT"],
        "lightning": ["PARROT", "BEE", "CAT", "VEX", "ALLAY", "PHANTOM"],
        "dark": ["BAT", "SPIDER", "ENDERMAN", "WARDEN", "CAVE_SPIDER", "WITHER_SKELETON"],
        "light": ["ALLAY", "IRON_GOLEM", "LLAMA", "AXOLOTL", "SNIFFER", "VILLAGER"],
        "nature": ["TURTLE", "PANDA", "FROG", "CAMEL", "HORSE", "RABBIT"]}
names = {"fire": ["Hỏa Hồ", "Viêm Linh", "Nham Thú", "Hỏa Mã", "Hỏa Trư", "Liệt Hỏa Trư"],
         "ice": ["Băng Hùng", "Tuyết Linh", "Sương Cốt", "Băng Dương", "Hàn Lang", "Tuyết Thố"],
         "lightning": ["Lôi Điểu", "Lôi Phong", "Lôi Miêu", "Lôi Linh", "Lôi Tinh", "Lôi Dực"],
         "dark": ["Dạ Bức", "Ám Chu", "Hư Không Nhân", "Ám Thú", "Độc Nhện", "Tử Cốt"],
         "light": ["Quang Tinh", "Thánh Vệ", "Quang Lạc", "Thủy Quang", "Thánh Giác", "Quang Sứ"],
         "nature": ["Huyền Quy", "Trúc Linh", "Thanh Oa", "Sa Lạc", "Lâm Mã", "Lâm Thố"]}
rar = ["common", "uncommon", "rare", "epic", "legendary", "mythic"]
roles = ["DPS", "TANK", "HEALER"]
skills_by_role = {"DPS": ["strike", "blaze_burst", "chain_bite"], "TANK": ["guard", "fortify", "taunt_roar"],
                  "HEALER": ["mend", "sanctuary", "vital_aura"]}
bases = {"common": (30, 4, 2, 10), "uncommon": (36, 5, 3, 11), "rare": (44, 6, 4, 12),
         "epic": (54, 8, 5, 13), "legendary": (66, 10, 7, 14), "mythic": (80, 13, 9, 15)}
out = ["# 42 pet mẫu: 6 hệ x 6 độ hiếm + 6 pet Ancient. Thêm pet mới: copy một khối, đổi id. Không giới hạn số lượng.",
       "# Trường tùy chọn: mythic-mob (id MythicMobs), icon (Material), evolutions (nhiều nhánh).",
       "pets:"]
for ei, (ek, eu) in enumerate(els):
    for i, rk in enumerate(rar):
        role = roles[(i + ei) % 3]
        hp, atk, df, sp = bases[rk]
        if role == "TANK": hp, df, atk = hp * 1.5, df * 1.6, atk * 0.7
        elif role == "HEALER": hp, atk = hp * 1.1, atk * 0.8
        else: atk *= 1.5
        sk = ", ".join(skills_by_role[role] + ["vitality", "power_up", "swift"])
        out.append(f"  {ek}_{rk}:")
        out.append(f'    name: "{names[ek][i]}"')
        out.append(f"    element: {eu}")
        out.append(f"    rarity: {rk.upper()}")
        out.append(f"    role: {role}")
        out.append(f"    entity: {ents[ek][i]}")
        out.append(f"    icon: {ents[ek][i]}_SPAWN_EGG")
        out.append(f"    base: {{hp: {hp:.0f}, atk: {atk:.1f}, def: {df:.1f}, speed: {sp}}}")
        out.append(f"    growth: {{hp: {hp/12:.2f}, atk: {atk/12:.2f}, def: {df/15:.2f}, speed: 0.1}}")
        out.append(f"    skills: [{sk}]")
        if i < 5:
            out.append("    evolutions:")
            out.append(f"      - {{to: {ek}_{rar[i+1]}, level: {10+i*8}, star: {min(i,3)}, souls: {200*(i+1)}, materials: {{{ek}_core: {i+1}}}}}")
            if i == 4:
                out.append(f"      - {{to: {ek}_ancient, level: 60, star: 3, souls: 5000, materials: {{ancient_essence: 3, {ek}_core: 10}}}}")
    out.append(f"  {ek}_ancient:")
    out.append(f'    name: "{names[ek][0]} Cổ Đại"')
    out.append(f"    element: {eu}")
    out.append("    rarity: ANCIENT")
    out.append("    role: DPS")
    out.append(f"    entity: {ents[ek][0]}")
    out.append("    icon: NETHER_STAR")
    out.append("    base: {hp: 100, atk: 24, def: 10, speed: 18}")
    out.append("    growth: {hp: 8, atk: 2, def: 0.7, speed: 0.15}")
    out.append("    skills: [strike, blaze_burst, chain_bite, vitality, power_up, swift]")
open("src/main/resources/pets.yml", "w", encoding="utf-8").write("\n".join(out) + "\n")
print("pets:", sum(1 for l in out if l.startswith("    element")))
