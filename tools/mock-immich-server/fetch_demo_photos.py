#!/usr/bin/env python3
"""
Downloads a library of real photos for the mock Immich server to show, for screenshots and demos.

    python3 tools/mock-immich-server/fetch_demo_photos.py
    python3 tools/mock-immich-server/mock_immich_server.py --photos ~/.cache/mock-immich-server/demo-photos

The photos are from StockSnap (https://stocksnap.io), which releases everything under CC0, so
screenshots of them can be used anywhere. Each is https://stocksnap.io/photo/<its ID below>.
They're laid out as --photos expects: a folder per album, and `people` with a face per person.

Needs Python 3.10+ and ImageMagick 7 (`magick`).
"""

import argparse
import subprocess
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

PHOTO_URL = "https://cdn.stocksnap.io/img-thumbs/960w/{}.jpg"

ALBUMS = {
    "Weekend at home": """
        CKT87YHJH0 SHZ91GEEC2 AJU8ODUJSV I8AQOA9IFG ERWVLD9KER LSPZTLDLOS SEHHFDFUBR QPEFUM8JJZ RFIS7TTABT
        H1YGYGDNIM XIE75UIHIS QJOFSPJVNX OJYJ8OE9OA 1QSVAPCGK5 BNAI6KA86B FHQOAULEKZ WAERGPSCVE XZYEK6Y6VE
        IR1NI4RTUN XNOUUIHJGG 6HSIWKMY2I""",
    "Picnic in the park": """
        OIUD9DEKPG RZ0ARMUB77 HBADKDBJYU 6G9JL1XVYS R0P6STPFE0 KPAKLGPS7E 3IVBNKC2JH LTKV70U67U HBKFP7NCLL
        MF5LAZWIOE YCX1QI6PKH 6KRBT8MYES FOMJTSRHS3 QWWUHIRMOL HE2S42Y7QG QCPBRN5SH3 GIU92HIZMC VSPHFEVRQB
        QH5EEYZZVG TWJG72KNKS QRAL4FD36P KOOJZRUJRZ WSRRONVX8J 66R7Q7VB6M ZMJIH1NTZ1 2Q8CXYKKAZ""",
    "Christmas baking": """
        ZK25PFAY2G NNKPN9QYIR LPKCDYIP2T R7BTCYGPXG QUR5HAYTAY WGKS3U5VWN OC4CTZYSNH GXXNDAHLCF VYXK7BAXN8
        7E8NHMZJUZ CXQWHJRHZX GO3ZJSS5Z1 CNVW0HDDVQ BMOGDZGHFQ EVLWWY2UJG OYLRKFFCJG YN7HDBKGZH 7N15JQQZVF
        KOAMLMIPSY EI32JIXJ7Q 3DPEHG8EOI ZJSI6MSWBQ X7MZJN39SM R7RWYRIPB0 W7LAPG4L9B U4MVLAMECU""",
    "Hiking in the mountains": """
        GGQX7WXAAC 5LXBN8H2CQ A150E71AFE Z5ZVY4FM28 LP0YZCWGC5 THN6VCGM6X ZU65CTAVWV 21D9441BD3 820A2B4E00
        EZZSLOHXVG 7WL5DGBFJ9 RGOP31CS3I NTDGYW24VE 442B6E6512 ESDO57UPJ2 ALZ2BEIMG7 VAW1ANPUSJ 4CB2CA6CFB
        9RZJVHG39A 73O22MWAEI 11ABE7BK82 ZHC95L56RT U75QCQN721 HCJ2DGBE0H COQUJR31GM Q16XY4FN0F N7WJCFTERE""",
    "Goa trip": """
        Z59RZ5SREK CCEUELWDKW EPRUD1TPG0 I4XBEISL6W ES9MSURD76 1HH5ZHYI3F B1YKNBZYFW DGGRJ554B0 FM9CFSXY0B
        I77JDV8AZQ 808DUPQ857 AWETGB1PS6 MB9BSCD0GM 3XY8033UKQ Z2FVSJZAEN KWBUZNDC9A 9UVAGMWV89 6EUED59RML
        9RH6AAYIDP NBETT9PEZG UK8ZUNVEG6 AMXMQQWNQJ JVSG0V22X6 63348D1909""",
    "Summer with friends": """
        6ZYX4YY4IR YBGQFVYDDC HFWBLKKCXV M16QHPDGYJ DDYC9U7O2P B740UADQ1E SXJ2NPC619 EVSKT1I1QG
        XO53SBWVMF E9QVYLY3DI TDLN8CRA4P MPU1111YLS LXJNKNODU0 5ZC9K92S09 YSWBX4I5PP Q7UIKF58IR F4ECAEBA04
        K4BK9Q3KL2 DEQLZPT7JI GYSA3ZJ6CM""",
    "Weekend away": """
        MIMZ4PUM2F FP4R72OQII 41SOJMWOYZ F4F265D113 2F6A2051DE 6QXTZFW51U ZK4IUPNIUE 6MWK0Y1FIZ QLWIIBIEWM
        CTYF2POOT3 DSGMWWUKM8 RYUWFU9ART 1C7C4EB4E1 94JZNS2H95 ABD3370968 0D54911F4D UPMXAGOQ77 3LU15A8E9F
        65IZ00R6EP 2EJYQBPJUU""",
    "Growing up": """
        XHT8C9KTXD TFOAKS2PEX 23J7I9NZO3 PZC8QNTP4Z NKQVF3AM4K Y3TGPUFVCH AL8LKF3BEN R0BZTCYBM6 AOMRJ0OTP7
        4B7G59YFTQ CIJXEU9VRY BQ4866DS8U DC7Y71MSMX JURGOKWYHI IDHVGQ9P8R""",
    "New York": """
        GEJ6ML9NHQ YF4F5MC5XS 39D8D5F9B2 4JV6F6555N XMR0X56U2X 4DA1218986 8C70ZM11P9 ERLE1JH6G6 ZALKF2S08S
        H9VZ3EATH3""",
    "Good food": """
        OC8WX0E0X3 A535FDBB21 8B74455531 9XFUIPUXDB DFZV36VZW4 PROXEBKRSP AWJD4WV6W1 3VLGQNBZP2 WIG7YVMEDF
        3J13XAT5LM K5T076FWTJ R0YJRQ45E0 R926LU1YEA CFBFE8F090 95U1U3BR0Y CR77K0B3GO YEBI1N2K7S""",
    "Birthday party": """
        7XQHYBOHLC P36YO6TM0I ITDXH9PPW6 W7T1J6B7DM ICAFQZXX7W DYHYR3HS48 N1A2BSB7WY UTVTUMZ4LW OJJY818LOZ
        WHWZKYQSDR HABABZA1UY DNWA3H3LCU IWWC5CHVYK 51B7CD882C 2N0WSXKY4L""",
    "Dogs": """
        JHPVE3XLUE RM97YA77HX 824XB7BXYU YNI4E0XAAS 4T0BQID9GS S2QPBLPTL5 19KEJ58BB9 REEH401AJS YAG9NYBYZW
        29D0358C71 ZBKV58494D 5JE3UK74TH 2SQMYBPQGK""",
    "Portraits": """
        FL7B8THF3Q KSXH58AKBR XKNXHW017D ZEXCRGH1GV OLT5TSPKH5 CLTJPNEBUL W6GFOSFAXA VTHPEAGBVR IDO6DVW9KN
        TI7XCCOF5U 8ENK7P4YSJ ZTNVVRFBWS 3LMPSCJQGQ IVZBYWKEFM TGG4JHNHB8 7BQNRHB6EX DAAZROYMQN W8I8JNL7W5
        UTEZRDTKPP X6BW9YMWXV Q3P6NCT23E 47FRU8SIHP WYOFLXRQQM NCT0MAJ5LE XX8XAN3RK3""",
}

# Each person's face: the photo it's in, where its centre is (0 to 1, across then down), and how
# wide the square around it is, as a share of the photo's width.
PEOPLE = {
    "Meera": ("IDO6DVW9KN", 0.54, 0.35, 0.36),
    "Maya": ("FL7B8THF3Q", 0.53, 0.45, 0.42),
    "Nina": ("KSXH58AKBR", 0.78, 0.44, 0.46),
    "Sofia": ("XKNXHW017D", 0.48, 0.40, 0.38),
    "Emma": ("ZEXCRGH1GV", 0.36, 0.32, 0.36),
    "Zoe": ("W6GFOSFAXA", 0.29, 0.50, 0.58),
    "Amara": ("VTHPEAGBVR", 0.47, 0.45, 0.50),
    "Karthik": ("TGG4JHNHB8", 0.46, 0.30, 0.30),
    "Daniel": ("3LMPSCJQGQ", 0.42, 0.41, 0.50),
    "Leo": ("ZTNVVRFBWS", 0.22, 0.36, 0.40),
    "Tom": ("IVZBYWKEFM", 0.49, 0.30, 0.30),
    "Sam": ("7BQNRHB6EX", 0.39, 0.24, 0.32),
}


def download(photo_id, path):
    if path.exists():
        return
    # The default Python user agent is turned away.
    request = urllib.request.Request(PHOTO_URL.format(photo_id), headers={"User-Agent": "Mozilla/5.0"})
    data = urllib.request.urlopen(request, timeout=60).read()
    temp = path.with_name(f"tmp-{path.name}")
    temp.write_bytes(data)
    temp.replace(path)


def crop_face(photo, face, x, y, size):
    width, height = map(int, subprocess.run(["magick", "identify", "-format", "%w %h", str(photo)],
                                            capture_output=True, text=True, check=True).stdout.split())
    side = min(round(size * width), width, height)
    left = min(max(round(x * width - side / 2), 0), width - side)
    top = min(max(round(y * height - side / 2), 0), height - side)
    subprocess.run(["magick", str(photo), "-crop", f"{side}x{side}+{left}+{top}", "+repage", "-quality", "92",
                    str(face)], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("folder", type=Path, nargs="?",
                        default=Path.home() / ".cache" / "mock-immich-server" / "demo-photos")
    folder = parser.parse_args().folder

    photos = {folder / album / f"{photo_id}.jpg": photo_id for album, ids in ALBUMS.items() for photo_id in ids.split()}
    for path in photos:
        path.parent.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(8) as pool:
        list(pool.map(lambda item: download(item[1], item[0]), photos.items()))

    by_id = {photo_id: path for path, photo_id in photos.items()}
    (folder / "people").mkdir(exist_ok=True)
    for name, (photo_id, x, y, size) in PEOPLE.items():
        crop_face(by_id[photo_id], folder / "people" / f"{name}.jpg", x, y, size)

    print(f"{len(photos)} photos in {len(ALBUMS)} albums and {len(PEOPLE)} people, in {folder}")


if __name__ == "__main__":
    main()
