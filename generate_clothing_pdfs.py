"""
Generate two educational clothing vocabulary PDFs using Pillow + NotoColorEmoji:
  1. clothing_names.pdf  — categories + emoji icons + item names
  2. clothing_pictures_only.pdf — categories + emoji icons only (quiz sheet)
"""

import io
import os
import textwrap
from PIL import Image, ImageDraw, ImageFont
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Image as RLImage
from reportlab.lib.units import inch

# ---------------------------------------------------------------------------
# Fonts
# ---------------------------------------------------------------------------

EMOJI_FONT_PATH = "/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf"
SANS_BOLD_PATH  = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
SANS_PATH       = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

# NotoColorEmoji is fixed-size (109 px per the CBDT table)
EMOJI_FONT      = ImageFont.truetype(EMOJI_FONT_PATH, 109)

def sans_bold(size): return ImageFont.truetype(SANS_BOLD_PATH, size)
def sans(size):      return ImageFont.truetype(SANS_PATH, size)

# ---------------------------------------------------------------------------
# Palette
# ---------------------------------------------------------------------------

BG_PAGE        = (250, 248, 245)
TITLE_BG       = (45,  52,  54)
TITLE_FG       = (255, 255, 255)
SUBTITLE_FG    = (200, 200, 200)

CATEGORIES = [
    {
        "emoji": "👕", "title": "TOPS",
        "hdr_bg": (255, 107, 107), "hdr_fg": (255, 255, 255),
        "cell_bg": (255, 235, 235), "cell_border": (255, 150, 150),
        "items": [
            ("👕", "T-Shirt"),   ("👔", "Shirt"),
            ("👚", "Blouse"),    ("🎽", "Tank Top"),
            ("✂️", "Crop Top"),  ("🏌️", "Polo Shirt"),
            ("🧶", "Sweater"),   ("🧥", "Hoodie"),
        ],
    },
    {
        "emoji": "🧥", "title": "OUTERWEAR",
        "hdr_bg": (78,  205, 196), "hdr_fg": (255, 255, 255),
        "cell_bg": (225, 250, 248), "cell_border": (100, 210, 200),
        "items": [
            ("🧥", "Jacket"),       ("🥼", "Coat"),
            ("🤵", "Blazer"),       ("☔", "Raincoat"),
            ("🏔️", "Parka"),        ("🕵️", "Trench Coat"),
            ("🧶", "Puffer Jacket"),("🧤", "Cardigan"),
        ],
    },
    {
        "emoji": "👖", "title": "BOTTOMS",
        "hdr_bg": (69,  183, 209), "hdr_fg": (255, 255, 255),
        "cell_bg": (225, 242, 252), "cell_border": (100, 180, 220),
        "items": [
            ("👖", "Pants"),     ("👖", "Jeans"),
            ("🩳", "Shorts"),    ("👗", "Skirt"),
            ("🩱", "Leggings"),  ("👖", "Trousers"),
            ("🧵", "Chinos"),    ("🏃", "Joggers"),
        ],
    },
    {
        "emoji": "👗", "title": "DRESSES & JUMPSUITS",
        "hdr_bg": (241, 196,  83), "hdr_fg": (80, 60, 0),
        "cell_bg": (255, 252, 220), "cell_border": (240, 200, 80),
        "items": [
            ("👗", "Dress"),     ("🌸", "Sundress"),
            ("👑", "Maxi Dress"),("💃", "Mini Dress"),
            ("🧑‍🔧", "Jumpsuit"), ("🎀", "Romper"),
        ],
    },
    {
        "emoji": "👟", "title": "FOOTWEAR",
        "hdr_bg": (162, 155, 254), "hdr_fg": (255, 255, 255),
        "cell_bg": (240, 235, 255), "cell_border": (180, 170, 250),
        "items": [
            ("👟", "Sneakers"),   ("👢", "Boots"),
            ("👠", "Heels"),      ("👡", "Sandals"),
            ("🥿", "Loafers"),    ("🩴", "Flip-Flops"),
            ("🥾", "Slippers"),   ("🩰", "Ballet Flats"),
        ],
    },
    {
        "emoji": "🎩", "title": "ACCESSORIES",
        "hdr_bg": (253, 121, 168), "hdr_fg": (255, 255, 255),
        "cell_bg": (255, 230, 242), "cell_border": (253, 150, 190),
        "items": [
            ("🎩", "Hat"),      ("🧢", "Cap"),
            ("🧣", "Scarf"),    ("🧤", "Gloves"),
            ("👜", "Belt"),     ("👔", "Tie"),
            ("🎀", "Bow Tie"), ("🕶️", "Sunglasses"),
        ],
    },
    {
        "emoji": "🩱", "title": "SWIMWEAR",
        "hdr_bg": (0,  184, 148), "hdr_fg": (255, 255, 255),
        "cell_bg": (220, 250, 240), "cell_border": (80, 200, 160),
        "items": [
            ("👙", "Bikini"),         ("🩱", "One-Piece Swimsuit"),
            ("🩲", "Swim Trunks"),    ("🏄", "Board Shorts"),
        ],
    },
    {
        "emoji": "🩲", "title": "UNDERWEAR & SOCKS",
        "hdr_bg": (253, 203, 110), "hdr_fg": (80, 50, 0),
        "cell_bg": (255, 248, 220), "cell_border": (253, 210, 100),
        "items": [
            ("🩲", "Underwear"),  ("🩳", "Boxers"),
            ("👙", "Bra"),        ("💪", "Sports Bra"),
            ("👚", "Camisole"),   ("🧦", "Socks"),
        ],
    },
]

# ---------------------------------------------------------------------------
# Layout constants (A4 @ 150 dpi = 1240 x 1754 px)
# ---------------------------------------------------------------------------

DPI     = 150
PW      = int(8.27  * DPI)   # 1240
PH      = int(11.69 * DPI)   # 1754
MARGIN  = 50
COLS    = 4

TITLE_H     = 130
HDR_H       = 50
CAT_GAP     = 18
CELL_PAD    = 12
RADIUS      = 14


# ---------------------------------------------------------------------------
# Drawing helpers
# ---------------------------------------------------------------------------

def rounded_rect(draw, xy, radius, fill, outline=None, width=2):
    x0, y0, x1, y1 = xy
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill,
                           outline=outline, width=width)


def draw_emoji(img, emoji_str, cx, cy, size=68):
    """Paste a colour emoji centered at (cx, cy). size is target px width."""
    tmp = Image.new("RGBA", (109, 109), (0, 0, 0, 0))
    d   = ImageDraw.Draw(tmp)
    d.text((0, 0), emoji_str, font=EMOJI_FONT, embedded_color=True)
    # trim and resize
    bbox = tmp.getbbox()
    if bbox:
        tmp = tmp.crop(bbox)
    tmp = tmp.resize((size, size), Image.LANCZOS)
    x = cx - tmp.width  // 2
    y = cy - tmp.height // 2
    img.paste(tmp, (x, y), tmp)


def draw_text_centered(draw, text, cx, y, font, fill):
    bbox = draw.textbbox((0, 0), text, font=font)
    w    = bbox[2] - bbox[0]
    draw.text((cx - w // 2, y), text, font=font, fill=fill)


def wrap_text(text, font, max_w, draw):
    """Return lines that fit within max_w pixels."""
    words  = text.split()
    lines  = []
    line   = ""
    for word in words:
        test = (line + " " + word).strip()
        bbox = draw.textbbox((0, 0), test, font=font)
        if bbox[2] - bbox[0] <= max_w:
            line = test
        else:
            if line:
                lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


# ---------------------------------------------------------------------------
# Measure a category block height
# ---------------------------------------------------------------------------

def cat_block_height(cat, show_names: bool, cell_w: int):
    n    = len(cat["items"])
    rows = (n + COLS - 1) // COLS
    if show_names:
        cell_h = 109 + 30 + CELL_PAD * 2   # emoji + name + padding
    else:
        cell_h = 109 + CELL_PAD * 2
    return HDR_H + rows * cell_h + CAT_GAP


# ---------------------------------------------------------------------------
# Draw a single category block at y, return new y
# ---------------------------------------------------------------------------

def draw_category(img, draw, cat, x0, y, content_w, show_names: bool):
    cell_w = content_w // COLS
    x1     = x0 + content_w
    n      = len(cat["items"])
    rows   = (n + COLS - 1) // COLS

    # ── header ──────────────────────────────────────────────────────────────
    rounded_rect(draw, (x0, y, x1, y + HDR_H), RADIUS,
                 fill=cat["hdr_bg"])
    hdr_font = sans_bold(18)
    text     = f"{cat['emoji']}  {cat['title']}"
    draw_text_centered(draw, text, (x0 + x1) // 2, y + (HDR_H - 22) // 2,
                       hdr_font, cat["hdr_fg"])
    y += HDR_H + 8

    # ── item grid ───────────────────────────────────────────────────────────
    name_font  = sans_bold(11)
    cell_inner = cell_w - 8                      # gap between cells
    if show_names:
        cell_h = 109 + 30 + CELL_PAD * 2
    else:
        cell_h = 109 + CELL_PAD * 2

    for r in range(rows):
        for c in range(COLS):
            idx = r * COLS + c
            if idx >= n:
                continue
            emoji_str, name = cat["items"][idx]

            cx0 = x0 + c * cell_w + 4
            cy0 = y + r * (cell_h + 6)
            cx1 = cx0 + cell_inner
            cy1 = cy0 + cell_h

            rounded_rect(draw, (cx0, cy0, cx1, cy1), RADIUS,
                         fill=cat["cell_bg"],
                         outline=cat["cell_border"], width=2)

            # emoji
            ecx = (cx0 + cx1) // 2
            ecy = cy0 + CELL_PAD + 109 // 2
            draw_emoji(img, emoji_str, ecx, ecy, size=68)

            # name label
            if show_names:
                label_y = cy0 + CELL_PAD + 109 + 4
                lines   = wrap_text(name, name_font, cell_inner - 8, draw)
                for line in lines:
                    draw_text_centered(draw, line, ecx, label_y,
                                       name_font, (45, 52, 54))
                    label_y += 14

    y += rows * (cell_h + 6)
    return y + CAT_GAP


# ---------------------------------------------------------------------------
# Build list of pages (PIL Images) for one PDF variant
# ---------------------------------------------------------------------------

def build_pages(show_names: bool):
    pages     = []
    content_w = PW - 2 * MARGIN

    # ── first page title area ──────────────────────────────────────────────
    page  = Image.new("RGB", (PW, PH), BG_PAGE)
    draw  = ImageDraw.Draw(page)

    # title banner gradient-ish (two-tone)
    rounded_rect(draw, (MARGIN, 30, PW - MARGIN, 30 + TITLE_H), 18,
                 fill=TITLE_BG)
    # decorative colour stripe inside banner
    stripe_colors = [(255,107,107),(78,205,196),(69,183,209),
                     (241,196,83),(162,155,254),(253,121,168),(0,184,148)]
    stripe_w = (content_w - 40) // len(stripe_colors)
    for i, sc in enumerate(stripe_colors):
        sx = MARGIN + 20 + i * stripe_w
        rounded_rect(draw, (sx, 38, sx + stripe_w - 4, 52), 6, fill=sc)

    big_font  = sans_bold(30)
    sub_font  = sans(13)
    draw_text_centered(draw, "CLOTHING VOCABULARY",
                       PW // 2, 60, big_font, TITLE_FG)
    sub_text = ("Learn the names of clothes in English!"
                if show_names else
                "Can you name these clothes?  ✏️")
    draw_text_centered(draw, sub_text,
                       PW // 2, 100, sub_font, SUBTITLE_FG)

    y = 30 + TITLE_H + 24

    for cat in CATEGORIES:
        needed = cat_block_height(cat, show_names, content_w)

        # does this block fit on the current page?
        if y + needed > PH - MARGIN:
            pages.append(page)
            page = Image.new("RGB", (PW, PH), BG_PAGE)
            draw = ImageDraw.Draw(page)
            y    = MARGIN

        y = draw_category(page, draw, cat, MARGIN, y, content_w, show_names)

    pages.append(page)
    return pages


# ---------------------------------------------------------------------------
# Save pages to PDF via reportlab (embed PNG images, one per page)
# ---------------------------------------------------------------------------

def save_pdf(pages, path):
    from reportlab.pdfgen import canvas as rl_canvas
    from reportlab.lib.utils import ImageReader

    a4_w, a4_h = A4   # points (72 pt/inch)
    c = rl_canvas.Canvas(path, pagesize=A4)
    for pil_img in pages:
        buf = io.BytesIO()
        pil_img.convert("RGB").save(buf, format="PNG")
        buf.seek(0)
        c.drawImage(ImageReader(buf), 0, 0, width=a4_w, height=a4_h)
        c.showPage()
    c.save()
    print(f"  ✓  {path}  ({len(pages)} page{'s' if len(pages)>1 else ''})")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    out_dir = os.path.dirname(os.path.abspath(__file__))
    print("Generating clothing vocabulary PDFs …\n")

    pages_names = build_pages(show_names=True)
    save_pdf(pages_names, os.path.join(out_dir, "clothing_names.pdf"))

    pages_pics  = build_pages(show_names=False)
    save_pdf(pages_pics,  os.path.join(out_dir, "clothing_pictures_only.pdf"))

    print("\nDone! 🎉")
