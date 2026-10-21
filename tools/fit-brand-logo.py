"""Fit a merchant/bank logo into the 256px square the round logo plate expects.

Usage: python tools/fit-brand-logo.py SOURCE app/src/main/res/drawable-nodpi/brand_<id>.png [--disc]

* A logo on its own solid colour (opaque, non-white corners) is kept whole: the app crops it to the circle.
* A logo on white or on transparency loses the empty margin around it, so it fills the plate.
* --disc: the source is a round badge on white; the area outside the disc takes the disc's edge colour,
  so the app treats it like a solid-colour logo and the white corners never show.
"""
import sys
from PIL import Image, ImageChops, ImageDraw

SIZE = 256


def solid_background(im):
    w, h = im.size
    corners = [im.getpixel(p) for p in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1))]
    return all(c[3] == 255 and min(c[:3]) < 235 for c in corners)


def content_box(im):
    alpha = im.getchannel("A")
    white = Image.new("RGBA", im.size, (255, 255, 255, 255))
    ink = ImageChops.difference(im, white).convert("L").point(lambda v: 255 if v > 24 else 0)
    return ImageChops.multiply(ink, alpha.point(lambda v: 255 if v > 16 else 0)).getbbox()


def square(im, margin, fill):
    side = max(im.size)
    side = round(side * (1 + 2 * margin))
    canvas = Image.new("RGBA", (side, side), fill)
    canvas.alpha_composite(im, ((side - im.width) // 2, (side - im.height) // 2))
    return canvas


def fit(source, disc=False):
    im = Image.open(source).convert("RGBA")
    if not disc and solid_background(im):
        return im.resize((SIZE, SIZE), Image.LANCZOS)
    box = content_box(im)
    if box: im = im.crop(box)
    if disc:
        im = square(im, 0, (255, 255, 255, 255)).resize((SIZE, SIZE), Image.LANCZOS)
        edge = im.getpixel((SIZE // 2, 3))
        mask = Image.new("L", im.size, 0)
        ImageDraw.Draw(mask).ellipse((2, 2, SIZE - 3, SIZE - 3), fill=255)
        return Image.composite(im, Image.new("RGBA", im.size, edge), mask)
    corner = Image.open(source).convert("RGBA").getpixel((0, 0))
    fill = (255, 255, 255, 255) if corner[3] == 255 else (0, 0, 0, 0)
    return square(im, 0.04, fill).resize((SIZE, SIZE), Image.LANCZOS)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    fit(args[0], "--disc" in sys.argv).save(args[1], optimize=True)
