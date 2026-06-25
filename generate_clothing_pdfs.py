"""
Generate two educational clothing vocabulary PDFs with a hand-crafted cursive style:
  1. clothing_names.pdf       — cursive names + colorful emoji cards
  2. clothing_pictures_only.pdf — emoji cards only + write-in lines (quiz sheet)

Fonts used:
  - Dancing Script Bold  (titles & category headers — connected cursive)
  - Caveat Bold/Regular  (item names — casual handwriting)
  - Noto Color Emoji     (full-color clothing emoji)
"""

import io
import os
import math
import random
from PIL import Image, ImageDraw, ImageFont
from reportlab.pdfgen import canvas as rl_canvas
from reportlab.lib.pagesizes import A4
from reportlab.lib.utils import ImageReader

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

REPO = os.path.dirname(os.path.abspath(__file__))
DANCING_BOLD   = os.path.join(REPO, "fonts", "DancingScript-Bold.ttf")
CAVEAT_BOLD    = os.path.join(REPO, "fonts", "Caveat-Bold.ttf")
CAVEAT_REG     = os.path.join(REPO, "fonts", "Caveat-Regular.ttf")
EMOJI_FONT_PATH = "/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf"

EMOJI_FONT = ImageFont.truetype(EMOJI_FONT_PATH, 109)

def fnt_dancing(sz): return ImageFont.truetype(DANCING_BOLD, sz)
def fnt_caveat_b(sz): return ImageFont.truetype(CAVEAT_BOLD, sz)
def fnt_caveat_r(sz): return ImageFont.truetype(CAVEAT_REG, sz)

# ---------------------------------------------------------------------------
# Categories
# ---------------------------------------------------------------------------

CATEGORIES = [
    {
        "title": "Tops",
        "cols": 4,
        "hdr_bg":  (255,  96,  96),   # coral-red
        "hdr_fg":  (255, 255, 255),
        "card_border": (255, 140, 140),
        "card_bg":     (255, 240, 240),
        "accent":      (255, 190, 190),
        "items": [
            ("👕", "T-Shirt"),   ("👔", "Shirt"),
            ("👚", "Blouse"),    ("🎽", "Tank Top"),
            ("✂️", "Crop Top"),  ("🏌️", "Polo Shirt"),
            ("🧶", "Sweater"),   ("🧥", "Hoodie"),
        ],
    },
    {
        "title": "Outerwear",
        "cols": 4,
        "hdr_bg":  ( 22, 190, 180),   # teal
        "hdr_fg":  (255, 255, 255),
        "card_border": ( 80, 210, 200),
        "card_bg":     (220, 252, 248),
        "accent":      (150, 230, 225),
        "items": [
            ("🧥", "Jacket"),        ("🥼", "Coat"),
            ("🤵", "Blazer"),        ("☔", "Raincoat"),
            ("🏔️", "Parka"),         ("🕵️", "Trench Coat"),
            ("🧶", "Puffer Jacket"), ("🧤", "Cardigan"),
        ],
    },
    {
        "title": "Bottoms",
        "cols": 4,
        "hdr_bg":  ( 55, 170, 210),   # sky-blue
        "hdr_fg":  (255, 255, 255),
        "card_border": (100, 190, 230),
        "card_bg":     (220, 240, 255),
        "accent":      (160, 210, 245),
        "items": [
            ("👖", "Pants"),    ("👖", "Jeans"),
            ("🩳", "Shorts"),   ("👗", "Skirt"),
            ("🩱", "Leggings"), ("👖", "Trousers"),
            ("🧵", "Chinos"),   ("🏃", "Joggers"),
        ],
    },
    {
        "title": "Dresses & Jumpsuits",
        "cols": 3,
        "hdr_bg":  (230, 175,  35),   # golden-yellow
        "hdr_fg":  ( 70,  45,   0),
        "card_border": (240, 200,  80),
        "card_bg":     (255, 252, 215),
        "accent":      (245, 220, 120),
        "items": [
            ("👗", "Dress"),      ("🌸", "Sundress"),
            ("👑", "Maxi Dress"), ("💃", "Mini Dress"),
            ("🧑‍🔧", "Jumpsuit"),  ("🎀", "Romper"),
        ],
    },
    {
        "title": "Footwear",
        "cols": 4,
        "hdr_bg":  (140, 130, 250),   # lavender
        "hdr_fg":  (255, 255, 255),
        "card_border": (175, 165, 255),
        "card_bg":     (238, 235, 255),
        "accent":      (200, 190, 255),
        "items": [
            ("👟", "Sneakers"),   ("👢", "Boots"),
            ("👠", "Heels"),      ("👡", "Sandals"),
            ("🥿", "Loafers"),    ("🩴", "Flip-Flops"),
            ("🥾", "Slippers"),   ("🩰", "Ballet Flats"),
        ],
    },
    {
        "title": "Accessories",
        "cols": 4,
        "hdr_bg":  (245, 100, 160),   # hot-pink
        "hdr_fg":  (255, 255, 255),
        "card_border": (255, 155, 195),
        "card_bg":     (255, 228, 242),
        "accent":      (255, 185, 220),
        "items": [
            ("🎩", "Hat"),      ("🧢", "Cap"),
            ("🧣", "Scarf"),    ("🧤", "Gloves"),
            ("👜", "Belt"),     ("👔", "Tie"),
            ("🎀", "Bow Tie"), ("🕶️", "Sunglasses"),
        ],
    },
    {
        "title": "Swimwear",
        "cols": 4,
        "hdr_bg":  (  0, 175, 140),   # mint-green
        "hdr_fg":  (255, 255, 255),
        "card_border": ( 60, 200, 160),
        "card_bg":     (215, 252, 238),
        "accent":      (130, 230, 200),
        "items": [
            ("👙", "Bikini"),            ("🩱", "One-Piece Swimsuit"),
            ("🩲", "Swim Trunks"),       ("🏄", "Board Shorts"),
        ],
    },
    {
        "title": "Underwear & Socks",
        "cols": 3,
        "hdr_bg":  (245, 190,  60),   # amber
        "hdr_fg":  ( 70,  45,   0),
        "card_border": (255, 215, 100),
        "card_bg":     (255, 250, 220),
        "accent":      (250, 225, 130),
        "items": [
            ("🩲", "Underwear"),  ("🩳", "Boxers"),
            ("👙", "Bra"),        ("💪", "Sports Bra"),
            ("👚", "Camisole"),   ("🧦", "Socks"),
        ],
    },
]

# ---------------------------------------------------------------------------
# Page dimensions  (A4 @ 150 dpi)
# ---------------------------------------------------------------------------

DPI    = 150
PW     = int(8.27  * DPI)   # 1240
PH     = int(11.69 * DPI)   # 1754
MARGIN = 45

TITLE_COLORS = [
    (255, 80,  80),  (255, 140,  0),  (210, 180,  0),
    ( 50, 180,  80), ( 50, 160, 220), (150, 100, 240),
    (240,  80, 160),
]

HDR_H      = 58
HDR_RADIUS = 18
CARD_GAP   = 8
CARD_VGAP  = 8
CAT_GAP    = 20


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def rrect(draw, xy, r, fill, outline=None, width=3):
    draw.rounded_rectangle(xy, radius=r, fill=fill, outline=outline, width=width)


def center_text(draw, text, cx, y, font, fill):
    bb = draw.textbbox((0, 0), text, font=font)
    w  = bb[2] - bb[0]
    draw.text((cx - w // 2, y), text, font=font, fill=fill)


def paste_emoji(img, emoji_str, cx, cy, size=74):
    """Render a colour emoji centred at (cx, cy)."""
    tmp = Image.new("RGBA", (109, 109), (0, 0, 0, 0))
    ImageDraw.Draw(tmp).text((0, 0), emoji_str, font=EMOJI_FONT,
                             embedded_color=True)
    bb = tmp.getbbox()
    if bb:
        tmp = tmp.crop(bb)
    tmp = tmp.resize((size, size), Image.LANCZOS)
    img.paste(tmp, (cx - tmp.width // 2, cy - tmp.height // 2), tmp)


def text_w(draw_or_none, text, font):
    dummy = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    bb = dummy.textbbox((0, 0), text, font=font)
    return bb[2] - bb[0]


def wrap(text, font, max_w):
    words = text.split()
    lines, cur = [], ""
    for w in words:
        test = (cur + " " + w).strip()
        if text_w(None, test, font) <= max_w:
            cur = test
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


# ---------------------------------------------------------------------------
# Background decoration  (scattered coloured dots)
# ---------------------------------------------------------------------------

DOT_COLORS = [
    (255, 200, 200), (200, 235, 255), (255, 255, 200),
    (200, 255, 230), (240, 210, 255), (255, 220, 240),
]

def draw_background(img):
    draw = ImageDraw.Draw(img)
    # warm cream
    draw.rectangle([0, 0, PW, PH], fill=(255, 253, 242))
    # subtle dot pattern
    rng = random.Random(7)
    for _ in range(120):
        x  = rng.randint(0, PW)
        y  = rng.randint(0, PH)
        r  = rng.randint(3, 10)
        c  = rng.choice(DOT_COLORS)
        draw.ellipse([x - r, y - r, x + r, y + r], fill=c)
    # thin rainbow border strip at top and bottom
    strip_colors = [(255,96,96),(255,160,0),(220,190,0),
                    (50,190,90),(50,165,225),(150,100,250),(245,100,160)]
    sw = PW // len(strip_colors)
    for i, sc in enumerate(strip_colors):
        x0 = i * sw
        draw.rectangle([x0, 0, x0 + sw, 8], fill=sc)
        draw.rectangle([x0, PH - 8, x0 + sw, PH], fill=sc)


# ---------------------------------------------------------------------------
# Title banner (page 1 only)
# ---------------------------------------------------------------------------

TITLE_H = 190

def draw_title(img, draw):
    """Draw the colourful cursive title and subtitle."""
    cx  = PW // 2
    # big rounded background
    rrect(draw, [MARGIN, 20, PW - MARGIN, 20 + TITLE_H], 24,
          fill=(40, 44, 52))

    # "Clothing Vocabulary" — each letter a different colour via word colouring
    big  = fnt_dancing(74)
    line1, line2 = "Clothing", "Vocabulary"
    colors_cycle = TITLE_COLORS

    # line 1
    y1 = 34
    words1 = [line1]
    x_cursor = cx - text_w(None, line1, big) // 2
    for wi, word in enumerate(words1):
        col = colors_cycle[wi % len(colors_cycle)]
        draw.text((x_cursor, y1), word, font=big, fill=col)
        x_cursor += text_w(None, word + " ", big)

    # line 2 — letter-by-letter rainbow
    y2 = y1 + 82
    chars2  = list(line2)
    total_w = text_w(None, line2, big)
    x_start = cx - total_w // 2
    x_cur   = x_start
    tmp_img = Image.new("RGBA", (PW, 100), (0, 0, 0, 0))
    tmp_drw = ImageDraw.Draw(tmp_img)
    for ci, ch in enumerate(chars2):
        col = TITLE_COLORS[ci % len(TITLE_COLORS)]
        tmp_drw.text((x_cur, 0), ch, font=big, fill=col)
        x_cur += text_w(None, ch, big)
    img.paste(tmp_img, (0, y2), tmp_img)

    # emoji decorations beside title
    paste_emoji(img, "👗", MARGIN + 55, 20 + TITLE_H // 2, size=52)
    paste_emoji(img, "👟", PW - MARGIN - 55, 20 + TITLE_H // 2, size=52)

    # subtitle
    sub = fnt_caveat_r(22)
    sub_text = "Learn the names of clothes in English!  ✨"
    cy_sub = 20 + TITLE_H - 35
    center_text(draw, sub_text, cx, cy_sub, sub, (200, 200, 200))


def draw_title_quiz(img, draw):
    """Title for the pictures-only quiz sheet."""
    cx = PW // 2
    rrect(draw, [MARGIN, 20, PW - MARGIN, 20 + TITLE_H], 24,
          fill=(40, 44, 52))
    big = fnt_dancing(74)
    line1 = "Clothing"
    line2 = "Vocabulary"
    draw.text((cx - text_w(None, line1, big) // 2, 34),
              line1, font=big, fill=(255, 200, 60))
    # line 2 rainbow
    chars2  = list(line2)
    total_w = text_w(None, line2, big)
    x_cur   = cx - total_w // 2
    y2 = 34 + 82
    tmp_img = Image.new("RGBA", (PW, 100), (0, 0, 0, 0))
    tmp_drw = ImageDraw.Draw(tmp_img)
    for ci, ch in enumerate(chars2):
        col = TITLE_COLORS[ci % len(TITLE_COLORS)]
        tmp_drw.text((x_cur, 0), ch, font=big, fill=col)
        x_cur += text_w(None, ch, big)
    img.paste(tmp_img, (0, y2), tmp_img)
    paste_emoji(img, "✏️", MARGIN + 55, 20 + TITLE_H // 2, size=52)
    paste_emoji(img, "📝", PW - MARGIN - 55, 20 + TITLE_H // 2, size=52)
    sub = fnt_caveat_r(22)
    center_text(draw, "Can you name all these clothes?  Write below each picture!",
                cx, 20 + TITLE_H - 35, sub, (200, 200, 200))


# ---------------------------------------------------------------------------
# Category header
# ---------------------------------------------------------------------------

def draw_cat_header(draw, cat, x0, y, content_w, img):
    x1 = x0 + content_w
    # main coloured bar
    rrect(draw, [x0, y, x1, y + HDR_H], HDR_RADIUS, fill=cat["hdr_bg"])
    # thin accent strip inside (bottom edge)
    darker = tuple(max(0, c - 40) for c in cat["hdr_bg"])
    rrect(draw, [x0 + 10, y + HDR_H - 10, x1 - 10, y + HDR_H - 2],
          6, fill=darker)
    # title text
    hf = fnt_dancing(28)
    center_text(draw, cat["title"], (x0 + x1) // 2,
                y + (HDR_H - 34) // 2, hf, cat["hdr_fg"])
    # small star decorations at edges
    sf = fnt_caveat_b(22)
    draw.text((x0 + 14, y + (HDR_H - 26) // 2), "✦", font=sf,
              fill=(255, 255, 255, 180))
    draw.text((x1 - 32, y + (HDR_H - 26) // 2), "✦", font=sf,
              fill=(255, 255, 255, 180))


# ---------------------------------------------------------------------------
# Item card
# ---------------------------------------------------------------------------

def card_dimensions(ncols, content_w, show_names):
    cell_w = (content_w - (ncols - 1) * CARD_GAP) // ncols
    emoji_size = 72   # fixed — fits 2 pages cleanly at 150 DPI
    if show_names:
        card_h = 10 + emoji_size + 8 + 28 + 8    # top + emoji + gap + name + bottom
    else:
        card_h = 10 + emoji_size + 8 + 22 + 8    # top + emoji + gap + write_line + bottom
    return cell_w, emoji_size, card_h


def draw_item_card(img, draw, cat, emoji_str, name, x0, y0,
                   cell_w, emoji_size, card_h, show_names):
    x1 = x0 + cell_w
    y1 = y0 + card_h
    # shadow (offset 3px)
    rrect(draw, [x0 + 3, y0 + 3, x1 + 3, y1 + 3], 14,
          fill=(200, 200, 200))
    # card background
    rrect(draw, [x0, y0, x1, y1], 14,
          fill=cat["card_bg"], outline=cat["card_border"], width=4)
    # inner accent line (decorative top stripe)
    rrect(draw, [x0 + 8, y0 + 6, x1 - 8, y0 + 12], 4,
          fill=cat["accent"])

    cx = (x0 + x1) // 2

    # emoji
    ey = y0 + 10 + emoji_size // 2
    if emoji_str:
        paste_emoji(img, emoji_str, cx, ey, size=emoji_size)

    # name or write-line
    text_top = y0 + 10 + emoji_size + 8

    if show_names:
        nf  = fnt_caveat_b(18)
        lines = wrap(name, nf, cell_w - 16)
        ly = text_top
        for line in lines[:2]:
            center_text(draw, line, cx, ly, nf, (40, 40, 50))
            ly += 20
    else:
        # dotted write-in line
        line_y = text_top + 14
        lx0    = x0 + 16
        lx1    = x1 - 16
        # draw dashed line
        dash_len, gap_len = 8, 5
        dx = lx0
        while dx < lx1:
            draw.line([(dx, line_y), (min(dx + dash_len, lx1), line_y)],
                      fill=cat["card_border"], width=2)
            dx += dash_len + gap_len
        # small pencil hint
        hint_f = fnt_caveat_r(11)
        center_text(draw, "write here", cx, line_y + 4,
                    hint_f, (180, 180, 180))


# ---------------------------------------------------------------------------
# Measure category block height
# ---------------------------------------------------------------------------

def cat_block_h(cat, show_names):
    n     = len(cat["items"])
    ncols = cat["cols"]
    nrows = math.ceil(n / ncols)
    content_w = PW - 2 * MARGIN
    _, _, card_h = card_dimensions(ncols, content_w, show_names)
    return HDR_H + 10 + nrows * (card_h + CARD_VGAP) + CAT_GAP


# ---------------------------------------------------------------------------
# Draw one category block, return new y
# ---------------------------------------------------------------------------

def draw_category(img, draw, cat, x0, y, content_w, show_names):
    draw_cat_header(draw, cat, x0, y, content_w, img)
    y += HDR_H + 10

    ncols = cat["cols"]
    cell_w, emoji_size, card_h = card_dimensions(ncols, content_w, show_names)
    items = cat["items"]
    nrows = math.ceil(len(items) / ncols)

    for r in range(nrows):
        for c in range(ncols):
            idx = r * ncols + c
            if idx >= len(items):
                break
            emoji_str, name = items[idx]
            cx0 = x0 + c * (cell_w + CARD_GAP)
            cy0 = y + r * (card_h + CARD_VGAP)
            draw_item_card(img, draw, cat, emoji_str, name,
                           cx0, cy0, cell_w, emoji_size, card_h, show_names)

    y += nrows * (card_h + CARD_VGAP)
    return y + CAT_GAP


# ---------------------------------------------------------------------------
# Decorative end-of-sheet flourish (fills blank space on last page)
# ---------------------------------------------------------------------------

def draw_flourish(img, draw, y, show_names):
    content_w = PW - 2 * MARGIN
    cx        = PW // 2
    space     = PH - MARGIN - y

    # colourful divider line
    div_colors = TITLE_COLORS
    seg_w = content_w // len(div_colors)
    for i, c in enumerate(div_colors):
        x0 = MARGIN + i * seg_w
        draw.rounded_rectangle([x0, y + 18, x0 + seg_w - 3, y + 26], 4, fill=c)
    y += 44

    # big star/celebration row
    stars     = ["⭐", "🌟", "✨", "🎉", "🏆", "🌈", "🎀"]
    star_size = min(60, space // 5)
    sx        = MARGIN + (content_w - len(stars) * (star_size + 14)) // 2
    for s in stars:
        paste_emoji(img, s, sx + star_size // 2, y + star_size // 2, size=star_size)
        sx += star_size + 14
    y += star_size + 14

    # motivational headline
    hf = fnt_dancing(52)
    msg = "Great job! You know all the clothes! 🎉" if show_names else \
          "Can you write all the names? You got this! ✏️"
    # wrap if too wide
    if text_w(None, msg, hf) > content_w:
        hf = fnt_dancing(36)
    center_text(draw, msg, cx, y, hf, (255, 96, 96))
    y += 58

    # count badge
    total = sum(len(c["items"]) for c in CATEGORIES)
    badge_text  = f"{total} clothing items"
    bf  = fnt_caveat_b(26)
    bw  = text_w(None, badge_text, bf) + 48
    bh  = 46
    bx0 = cx - bw // 2
    rrect(draw, [bx0, y, bx0 + bw, y + bh], 23,
          fill=(255, 235, 60), outline=(255, 180, 0), width=3)
    center_text(draw, badge_text, cx, y + 10, bf, (70, 50, 0))
    y += bh + 16

    # small clothing parade
    parade = ["👕","👖","👗","👟","🎩","👙","🧣","🧦","👔","🥾"]
    ps     = min(44, space // 8)
    px     = MARGIN + (content_w - len(parade) * (ps + 10)) // 2
    for e in parade:
        paste_emoji(img, e, px + ps // 2, y + ps // 2, size=ps)
        px += ps + 10


# ---------------------------------------------------------------------------
# Build pages
# ---------------------------------------------------------------------------

def build_pages(show_names: bool):
    pages = []
    content_w = PW - 2 * MARGIN

    def new_page():
        pg = Image.new("RGBA", (PW, PH), (255, 253, 242, 255))
        draw_background(pg)
        return pg, ImageDraw.Draw(pg)

    page, draw = new_page()

    if show_names:
        draw_title(page, draw)
    else:
        draw_title_quiz(page, draw)

    y = 20 + TITLE_H + 22

    for cat in CATEGORIES:
        needed = cat_block_h(cat, show_names)
        if y + needed > PH - MARGIN:
            pages.append(page)
            page, draw = new_page()
            y = MARGIN

        y = draw_category(page, draw, cat, MARGIN, y, content_w, show_names)

    # fill remaining space on last page
    if PH - MARGIN - y > 280:
        draw_flourish(page, draw, y, show_names)

    pages.append(page)
    return pages


# ---------------------------------------------------------------------------
# Save pages → PDF
# ---------------------------------------------------------------------------

def save_pdf(pages, path):
    a4_w, a4_h = A4
    c = rl_canvas.Canvas(path, pagesize=A4)
    for pg in pages:
        buf = io.BytesIO()
        pg.convert("RGB").save(buf, format="PNG")
        buf.seek(0)
        c.drawImage(ImageReader(buf), 0, 0, width=a4_w, height=a4_h)
        c.showPage()
    c.save()
    print(f"  ✓  {path}  ({len(pages)} page{'s' if len(pages) > 1 else ''})")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    out = REPO
    print("Generating clothing vocabulary PDFs …\n")

    pages_names = build_pages(show_names=True)
    save_pdf(pages_names, os.path.join(out, "clothing_names.pdf"))

    pages_pics = build_pages(show_names=False)
    save_pdf(pages_pics,  os.path.join(out, "clothing_pictures_only.pdf"))

    print("\nDone! 🎉")
