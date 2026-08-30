#!/usr/bin/env python3
"""dopagaki README用モックアップSVG生成"""

FONT = "Noto Sans CJK JP, sans-serif"

# 色
FRAME = "#17171a"; FRAME_S = "#2f2f36"; SCREEN = "#000000"
VIDEO_BG = "#15151c"; RED = "#FF0033"; WHITE = "#E7E9EA"
GRAY = "#8E8E93"; DIM = "#3A3A3C"; BAR1 = "#3A3A44"; BAR2 = "#26262C"
BAR3 = "#30303A"; CARD_SEP = "#1f1f24"; GLYPH = "#4a4a55"; PANEL = "#17171d"

SX, SY, SW, SH = 22, 22, 316, 716  # screen rect
DIV_Y = 302  # divider center


def phone_base(brand=True):
    s = []
    s.append(f'<rect x="10" y="10" width="340" height="740" rx="28" fill="{FRAME}" stroke="{FRAME_S}" stroke-width="2"/>')
    s.append(f'<rect x="{SX}" y="{SY}" width="{SW}" height="{SH}" rx="18" fill="{SCREEN}"/>')
    s.append(f'<circle cx="180" cy="36" r="4.5" fill="#15151a"/>')
    if brand:
        # ブランドバー: ミニアイコン + dopagaki
        s.append(f'<rect x="145" y="53" width="13" height="4.5" rx="2.2" fill="{RED}"/>')
        s.append(f'<rect x="145" y="60.5" width="13" height="4.5" rx="2.2" fill="{WHITE}"/>')
        s.append(f'<text x="165" y="64.5" font-family="{FONT}" font-size="11" letter-spacing="1.5" fill="{GRAY}">dopagaki</text>')
    return s


def video_pane(title=True):
    s = []
    s.append(f'<rect x="{SX}" y="74" width="{SW}" height="225" fill="{VIDEO_BG}"/>')
    if title:
        s.append(f'<text x="36" y="98" font-family="{FONT}" font-size="10.5" fill="{WHITE}">今日も一日タイムラインを眺める動画</text>')
        s.append(f'<circle cx="41" cy="110" r="5" fill="{DIM}"/>')
        s.append(f'<text x="51" y="113.5" font-family="{FONT}" font-size="8.5" fill="{GRAY}">dopa_ch</text>')
    # 再生ボタン
    s.append(f'<rect x="148" y="152" width="64" height="44" rx="12" fill="{RED}"/>')
    s.append(f'<path d="M173,164 l20,10 -20,10 z" fill="#ffffff"/>')
    # シークバー
    s.append(f'<text x="38" y="278" font-family="{FONT}" font-size="9" fill="{GRAY}">3:24 / 12:08</text>')
    s.append(f'<rect x="38" y="285" width="284" height="3" rx="1.5" fill="{DIM}"/>')
    s.append(f'<rect x="38" y="285" width="108" height="3" rx="1.5" fill="{RED}"/>')
    s.append(f'<circle cx="146" cy="286.5" r="4" fill="#ffffff"/>')
    return s


def divider():
    return [
        f'<rect x="{SX}" y="{DIV_Y - 1.5}" width="{SW}" height="3" fill="{DIM}"/>',
        f'<rect x="158" y="{DIV_Y - 3}" width="44" height="6" rx="3" fill="{GRAY}"/>',
    ]


def action_glyphs(x, y):
    s = []
    s.append(f'<path transform="translate({x},{y})" d="M3,0 h9 a3,3 0 0 1 3,3 v4 a3,3 0 0 1 -3,3 h-4 l-3,3 v-3 a3,3 0 0 1 -2,-3 v-4 a3,3 0 0 1 3,-3 z" fill="{GLYPH}" opacity="0.9"/>')
    s.append(f'<path transform="translate({x + 62},{y})" d="M2,9 h9 v-2.5 l4.5,4 -4.5,4 v-2.5 h-9 z M14,4 h-9 v2.5 l-4.5,-4 4.5,-4 v2.5 h9 z" transform-origin="center" fill="{GLYPH}" opacity="0.9"/>')
    s.append(f'<path transform="translate({x + 128},{y})" d="M6.5,12 C1.5,8 0.5,4 3,2.2 4.7,1 6,2 6.5,3.2 7,2 8.3,1 10,2.2 12.5,4 11.5,8 6.5,12 z" fill="{GLYPH}" opacity="0.9"/>')
    for i, h in enumerate((5, 9, 7)):
        s.append(f'<rect x="{x + 190 + i * 4.5}" y="{y + 12 - h}" width="3" height="{h}" rx="1" fill="{GLYPH}" opacity="0.9"/>')
    return s


def tweet_card(y0, text_widths, with_image=False):
    s = []
    s.append(f'<circle cx="48" cy="{y0 + 16}" r="14" fill="{BAR2}"/>')
    s.append(f'<rect x="70" y="{y0 + 6}" width="66" height="9" rx="4" fill="{BAR1}"/>')
    s.append(f'<rect x="142" y="{y0 + 7}" width="44" height="7" rx="3" fill="{BAR2}"/>')
    ty = y0 + 24
    for w in text_widths:
        s.append(f'<rect x="70" y="{ty}" width="{w}" height="8" rx="4" fill="{BAR3}"/>')
        ty += 14
    if with_image:
        s.append(f'<rect x="70" y="{ty + 2}" width="236" height="52" rx="8" fill="#1d1d24"/>')
        s.append(f'<circle cx="102" cy="{ty + 20}" r="6" fill="{DIM}"/>')
        s.append(f'<path d="M78,{ty + 46} l24,-18 16,12 14,-9 42,15 z" fill="{DIM}"/>')
        ty += 58
    s.extend(action_glyphs(70, ty + 6))
    end = ty + 26
    s.append(f'<rect x="{SX}" y="{end}" width="{SW}" height="1.2" fill="{CARD_SEP}"/>')
    return s, end


def x_pane_cards():
    s = []
    y = 316
    card, y = tweet_card(y, (236, 208, 150), with_image=False)
    s += card
    card, y = tweet_card(y + 8, (232, 180), with_image=True)
    s += card
    card, y = tweet_card(y + 8, (236, 214, 120), with_image=False)
    s += card
    return s


def clip_screen(inner):
    return (
        f'<clipPath id="scr"><rect x="{SX}" y="{SY}" width="{SW}" height="{SH}" rx="18"/></clipPath>'
        f'<g clip-path="url(#scr)">{"".join(inner)}</g>'
    )


def svg(width, height, body):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}">{body}</svg>'
    )


# ---- mock 1: メイン画面 ----
inner = video_pane() + x_pane_cards() + divider()
body = "".join(phone_base()) + clip_screen(inner)
open("main.svg", "w").write(svg(360, 760, body))

# ---- mock 2: 縦メニュー表示 ----
menu = []
menu.append(f'<rect x="4" y="400" width="72" height="252" rx="14" fill="{PANEL}" stroke="#2c2c34" stroke-width="1.5"/>')
icon = f'stroke="{WHITE}" stroke-width="1.9" fill="none" stroke-linecap="round" stroke-linejoin="round"'
cx = 40
ys = [424, 474, 524, 574, 624]
menu.append(f'<g transform="translate({cx - 11},{ys[0] - 11})"><path d="M3,11 L11,3.5 L19,11 M6,9.5 V18.5 H16 V9.5" {icon}/></g>')
menu.append(f'<g transform="translate({cx - 11},{ys[1] - 11})"><circle cx="9" cy="9" r="5.5" {icon}/><path d="M13.5,13.5 L18.5,18.5" {icon}/></g>')
menu.append(f'<g transform="translate({cx - 11},{ys[2] - 11})"><path d="M11,3 V19 M4,7 L18,15 M18,7 L4,15" {icon}/></g>')
menu.append(f'<g transform="translate({cx - 11},{ys[3] - 11})"><path d="M11,3.5 a5.5,5.5 0 0 1 5.5,5.5 v4 l2.5,3.5 H3 l2.5,-3.5 v-4 a5.5,5.5 0 0 1 5.5,-5.5 z M9,17.5 a2,2 0 0 0 4,0" {icon}/></g>')
menu.append(f'<g transform="translate({cx - 11},{ys[4] - 11})"><rect x="3" y="5.5" width="16" height="11.5" rx="2" {icon}/><path d="M4.5,7.5 L11,12.5 L17.5,7.5" {icon}/></g>')
# スワイプ矢印（半透明の下敷き付き）
menu.append(f'<rect x="80" y="492" width="212" height="78" rx="12" fill="#000000" opacity="0.62"/>')
for i, op in enumerate((1.0, 0.55, 0.25)):
    x0 = 88 + i * 13
    menu.append(f'<path d="M{x0},508 l10,12 -10,12" stroke="{RED}" stroke-width="3.5" fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="{op}"/>')
menu.append(f'<text x="88" y="556" font-family="{FONT}" font-size="10.5" fill="{GRAY}">左端から右へスワイプ</text>')
inner2 = video_pane() + x_pane_cards() + divider() + menu
body2 = "".join(phone_base()) + clip_screen(inner2)
open("menu.svg", "w").write(svg(360, 760, body2))

# ---- mock 3: 操作ガイド ----
marks = []


def marker(n, x, y):
    marks.append(f'<circle cx="{x}" cy="{y}" r="10" fill="{RED}"/>')
    marks.append(f'<text x="{x}" y="{y + 4}" text-anchor="middle" font-family="{FONT}" font-size="11.5" font-weight="bold" fill="#fff">{n}</text>')


def callout(x1, y1, x2, y2):
    marks.append(f'<path d="M{x1},{y1} L{x2},{y2}" stroke="{GRAY}" stroke-width="1.2" stroke-dasharray="4 3" fill="none"/>')


labels = []


def label(n, x, y, title, lines):
    labels.append(f'<circle cx="{x + 10}" cy="{y - 4}" r="10" fill="{RED}"/>')
    labels.append(f'<text x="{x + 10}" y="{y}" text-anchor="middle" font-family="{FONT}" font-size="11.5" font-weight="bold" fill="#fff">{n}</text>')
    labels.append(f'<text x="{x + 28}" y="{y}" font-family="{FONT}" font-size="13" font-weight="bold" fill="{WHITE}">{title}</text>')
    for i, ln in enumerate(lines):
        labels.append(f'<text x="{x + 28}" y="{y + 19 + i * 17}" font-family="{FONT}" font-size="11.5" fill="{GRAY}">{ln}</text>')


inner3 = video_pane(title=False) + x_pane_cards() + divider()
body3 = "".join(phone_base()) + clip_screen(inner3)

marker(1, 236, 174); callout(246, 174, 400, 105)
marker(2, 180, DIV_Y); callout(190, DIV_Y, 400, 235)
marker(3, 180, 328); callout(190, 328, 400, 370)
marker(4, 34, 186); marker(4, 34, 520); callout(44, 520, 400, 470)
marker(5, 326, 620); callout(336, 620, 400, 585)

label(1, 400, 105, "動画は再生開始で自動全画面",
      ["上ペインの枠内だけで全画面になり、", "下の X はそのまま操作できる"])
label(2, 400, 235, "境界線ハンドル（唯一の常設UI）",
      ["ドラッグ: 比率変更（15%〜85%）", "ダブルタップ: 1:2 に戻す", "長押しして離す: ペインを再読み込み"])
label(3, 400, 370, "ペイン上端から下スワイプ",
      ["X のヘッダーを一時表示"])
label(4, 400, 470, "左端中央から右スワイプ",
      ["そのペインの操作メニューを", "左端に縦表示（4秒で自動格納）"])
label(5, 400, 585, "バックジェスチャー",
      ["最後に触ったペインで戻る", "（全画面中は全画面解除が最優先）"])

body3 += "".join(marks) + "".join(labels)
open("gestures.svg", "w").write(svg(680, 760, body3))
print("done")
