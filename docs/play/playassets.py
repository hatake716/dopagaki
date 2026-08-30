#!/usr/bin/env python3
"""Google Play 用アセット生成: 512px アイコン / 1024x500 フィーチャーグラフィック"""

FONT = "Noto Sans CJK JP, sans-serif"
RED = "#FF0033"; WHITE = "#E7E9EA"; GRAY = "#8E8E93"; BG = "#101014"


def svg(w, h, body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}">{body}</svg>')


# ---- アイコン 512x512（Play はマスクを後掛けするのでフルスクエア） ----
s = []
s.append(f'<rect width="512" height="512" fill="{BG}"/>')
# ランチャーアイコンと同じモチーフ: 上=YouTube(赤バー) / 境界ピル / 下=X(白バー)
s.append(f'<rect x="128" y="150" width="256" height="95" rx="26" fill="{RED}"/>')
s.append(f'<rect x="196" y="266" width="120" height="19" rx="9.5" fill="{GRAY}"/>')
s.append(f'<rect x="128" y="306" width="256" height="95" rx="26" fill="{WHITE}"/>')
open("icon-512.svg", "w").write(svg(512, 512, "".join(s)))

# ---- フィーチャーグラフィック 1024x500 ----
f = []
f.append(f'<rect width="1024" height="500" fill="{BG}"/>')
# 左: ミニ2ペインのモチーフ
f.append('<rect x="96" y="110" width="200" height="280" rx="24" fill="#000" stroke="#2f2f36" stroke-width="3"/>')
f.append('<rect x="118" y="140" width="156" height="74" rx="10" fill="#1b1b22"/>')
f.append('<circle cx="196" cy="172" r="17" fill="#33333c"/>')
f.append('<path d="M190,163 l16,9 -16,9 z" fill="#E7E9EA"/>')
f.append('<rect x="126" y="202" width="140" height="4" rx="2" fill="#3A3A3C"/>')
f.append(f'<rect x="126" y="202" width="56" height="4" rx="2" fill="{RED}"/>')
f.append(f'<rect x="166" y="226" width="60" height="9" rx="4.5" fill="{GRAY}"/>')
# Xタイムライン風のバー
for i, (w1, w2) in enumerate(((120, 90), (132, 70), (110, 96))):
    y = 252 + i * 40
    f.append(f'<circle cx="132" cy="{y + 8}" r="10" fill="#26262C"/>')
    f.append(f'<rect x="150" y="{y}" width="{w1}" height="8" rx="4" fill="#3A3A44"/>')
    f.append(f'<rect x="150" y="{y + 13}" width="{w2}" height="7" rx="3.5" fill="#30303A"/>')
# 右: ワードマークとコピー
f.append(f'<rect x="380" y="196" width="44" height="15" rx="7" fill="{RED}"/>')
f.append(f'<rect x="380" y="219" width="44" height="15" rx="7" fill="{WHITE}"/>')
f.append(f'<text x="446" y="232" font-family="{FONT}" font-size="64" font-weight="bold" '
         f'letter-spacing="4" fill="{WHITE}">dopagaki</text>')
f.append(f'<text x="448" y="290" font-family="{FONT}" font-size="27" fill="{GRAY}">'
         '動画を見ながら、タイムラインを流し読み。</text>')
f.append(f'<text x="448" y="330" font-family="{FONT}" font-size="21" fill="#5a5a60">'
         '2ペイン同時表示・没入特化のながら見ブラウザ</text>')
open("feature-graphic.svg", "w").write(svg(1024, 500, "".join(f)))
print("done")
