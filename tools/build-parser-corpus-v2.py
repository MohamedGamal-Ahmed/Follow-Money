"""Author the unapproved v2 draft from explicit masked examples, never from parser output.

This is fixture tooling, not a production parsing stage. v1 is kept unchanged for historical comparison.
The draft can be structurally valid while its independently reported readiness gates still fail.

Owner policy (21 October 2026): the corpus proves that the generic logic handles every message type and
writing shape; it does not prove coverage of particular banks. A message type without an observed sample is
covered by a labelled synthetic base case (group synthetic_coverage, evidence level synthetic) attributed to
PUBLIC_FORMAT_<bank> when built on a published format, otherwise SYNTHETIC_<type>. Sources with real redacted
samples (Banque Misr, HSBC, QNB, Telda, TRU) never receive a synthetic alternative format.
"""
from __future__ import annotations

import csv
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
import re
import unicodedata

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / "tools/fixtures"
EXTRA = """expected_fee_minor expected_fee_currency expected_account_last4 expected_counterparty_token
expected_movement_kind expected_direction_certainty expected_equivalent_minor expected_equivalent_currency
expected_credit_limit_minor expected_credit_limit_currency expected_expiry_date expected_time_ambiguity
expected_year_origin context_group expected_standalone_decision expected_context_decision
source_evidence_id base_case_id variant feature_tags expected_accounting_effect""".split()
FROZEN = {"notification_fields", "dedupe_identity", "promotions", "other_and_failed"}
BASE = int(datetime(2026, 10, 21, 20, tzinfo=timezone.utc).timestamp() * 1000)


def decode(value):
    if value == r"\N":
        return None
    return re.sub(r"\\(u[0-9a-fA-F]{4}|[nrt\\])", lambda m: chr(int(m[1][1:], 16)) if m[1].startswith("u") else {"n": "\n", "r": "\r", "t": "\t", "\\": "\\"}[m[1]], value)


def encode(value):
    if value is None:
        return r"\N"
    out = []
    for char in str(value):
        if char in {"\\", "\n", "\r", "\t"}:
            out.append({"\\": r"\\", "\n": r"\n", "\r": r"\r", "\t": r"\t"}[char])
        elif ord(char) in {0x00a0, 0x202f, 0x2009, 0x200e, 0x200f, *range(0x202a, 0x202f)}:
            out.append(f"\\u{ord(char):04x}")
        else:
            out.append(char)
    return "".join(out)


with (FIXTURES / "parser-corpus.tsv").open(encoding="utf-8", newline="") as source:
    reader = csv.DictReader(source, delimiter="\t", quoting=csv.QUOTE_NONE)
    ORIGINAL = list(reader.fieldnames)
    v1 = [{k: decode(v) for k, v in row.items()} for row in reader]
COLUMNS = ORIGINAL + EXTRA
rows = []
for original in v1:
    if original["group"] == "source_independence":
        continue
    row = dict.fromkeys(COLUMNS)
    row.update(original)
    row.update(source_evidence_id="legacy-v1", base_case_id=row["id"], variant="retained_v1",
               expected_direction_certainty="explicit" if row["expected_direction"] else None,
               feature_tags=row["group"])
    if row["id"] == "p134":
        row.update(expected_decision="AUTO", expected_fee_minor=500, expected_fee_currency="EGP",
                   expected_movement_kind="transfer_out", required_reasons="COMPLETED_EVENT|AMOUNT_UNAMBIGUOUS|DIRECTION_OUT|EXPLICIT_FEE",
                   note="Owner-corrected v2 oracle: principal EGP 500, separate optional fee EGP 5; no fee inference or principal/fee sum.")
    elif row["id"] == "p137":
        row.update(expected_amount_minor=1250, expected_currency="USD", expected_decision="AUTO", expected_capture="NEW",
                   required_reasons="COMPLETED_EVENT|AMOUNT_UNAMBIGUOUS|DIRECTION_OUT",
                   note="Owner-corrected v2 oracle: bare dollar sign defaults to USD. Review conflicting explicit dollar currencies separately.")
    elif row["id"] == "p140":
        row.update(expected_direction="out", expected_direction_certainty="likely", expected_decision="REVIEW", expected_capture="NEW",
                   required_reasons="CARD_MERCHANT_CONTEXT|INFERRED_OUTGOING_DIRECTION",
                   note="Owner-corrected v2 oracle: card plus merchant supports likely outgoing direction; absent explicit financial verb requires review.")
    rows.append(row)


def case(identifier, source, text, *, group="user_masked", kind="transaction", amount=None, currency="EGP", direction=None,
         merchant=None, balance=None, bcur=None, card=None, account=None, movement=None,
         decision="AUTO", reasons=None, dt=None, precision="unknown", evidence=None, tags=None, **extra):
    row = dict.fromkeys(COLUMNS)
    row.update(id=identifier, group=group, language="mixed", channel="sms", source=source,
               source_message_id="masked-fixture-" + identifier, received_at_ms=BASE + len(rows) * 1000,
               notification_flags=0, body=text, expected_type=kind,
               expected_amount_minor=amount, expected_currency=currency if kind == "transaction" else None,
               expected_direction=direction, expected_merchant_key=merchant, expected_balance_minor=balance,
               expected_balance_currency=(bcur or currency) if balance is not None else None,
               expected_card_last4=card, expected_datetime=dt, expected_time_precision=precision,
               expected_decision=decision, required_reasons=reasons or ("COMPLETED_EVENT|AMOUNT_UNAMBIGUOUS|DIRECTION_" + direction.upper()),
               expected_capture="NEW" if decision in {"AUTO", "REVIEW"} else "NONE",
               expected_account_last4=account, expected_movement_kind=movement,
               expected_direction_certainty="explicit" if direction else None,
               source_evidence_id=evidence or identifier, base_case_id=identifier, variant="base",
               feature_tags=tags or movement or kind,
               note="User-supplied masked message; numerical values are examples. Expected values authored independently of both parsers.")
    row.update(extra)
    if set(row) - set(COLUMNS):
        raise ValueError(f"Unknown fixture columns: {set(row) - set(COLUMNS)}")
    return row


OBSERVED_SOURCES = ("BANKMISR", "BANQUEMISR", "BANQUE_MISR", "HSBC", "QNB", "TELDA", "TRU")


def synthetic(identifier, source, text, *, note, **kwargs):
    """Labelled synthetic base case for a message type with no observed sample; never presented as observed."""
    if not re.fullmatch(r"(PUBLIC_FORMAT|SYNTHETIC)_[A-Z0-9_]+", source):
        raise ValueError(f"Synthetic source must be PUBLIC_FORMAT_<bank> or SYNTHETIC_<type>: {source}")
    if re.sub(r"^(PUBLIC_FORMAT|SYNTHETIC)_", "", source).startswith(OBSERVED_SOURCES):
        raise ValueError(f"No synthetic alternative format for an observed source: {source}")
    row = case(identifier, source, text, group="synthetic_coverage", evidence=source, **kwargs)
    row.update(source_message_id="synthetic-fixture-" + identifier, note="SYNTHETIC: " + note)
    return row


def field_order(text):
    """Declared test permutation of complete fields/clauses, not another observed bank message."""
    if " Your available balance is " in text:
        head, balance = text.split(" Your available balance is ", 1)
        return "Your available balance is " + balance + "; " + head
    if text.startswith("IPN transfer") and ". Ref# " in text:
        head, reference = text.split(". Ref# ", 1)
        return "Ref# " + reference + " " + head + "."
    date = re.fullmatch(r"(.+?) فى (\d{2}-[A-Za-z]+-\d{4})(.*)", text)
    if date:
        return "فى " + date[2] + "، " + date[1] + date[3]
    if ". " in text:
        head, rest = text.split(". ", 1)
        return rest + " " + head + "."
    if "، " in text:
        head, rest = text.split("، ", 1)
        return rest + "، " + head
    return text.replace(" ", "\n", 1)


def family(row, count=3):
    """Base plus three mechanical resilience variants. Not three new observed bank formats."""
    rows.append(row)
    transforms = [
        ("digits", lambda t: t.translate(str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩"))),
        ("spaces", lambda t: "\u200f" + t.replace(" ", "\u00a0  ")),
        ("field_order", field_order),
    ]
    for name, transform in transforms[:count]:
        clone = dict(row)
        clone.update(id=row["id"] + "-" + name, source_message_id=row["source_message_id"] + "-" + name,
                     body=transform(row["body"]), variant=name,
                     note=("SYNTHETIC: " if row["group"] == "synthetic_coverage" else "") +
                          "Generated " + name + " variation of " + row["id"] + "; not another observed source format.")
        # A single-clause input may have no breakable boundary. Add a line break at its first space instead.
        if clone["body"] == row["body"]:
            clone["body"] = row["body"].replace(" ", "\n", 1)
        rows.append(clone)


family(case("u01", "BankMisr", "تم اضافة مبلغ 500EGP الى حساب رقم xxx1234 فى 21-Oct-2026 عن طريق التحويل اللحظي", amount=50000, direction="in", account="1234", movement="instant_transfer_in", dt="2026-10-21", precision="date", tags="instant_transfer_in|missing_merchant_allowed"))
base_in = next(r for r in rows if r["id"] == "u01")
for suffix, raw, minor in [("amount4", "1500", 150000), ("amount5", "15000", 1500000)]:
    clone = dict(base_in); clone.update(id="u01-"+suffix, source_message_id="masked-fixture-u01-"+suffix,
        body=base_in["body"].replace("500EGP", raw+"EGP"), expected_amount_minor=minor, variant=suffix,
        note="Owner-requested different-length amount variant of u01; same source form.")
    rows.append(clone)
for suffix, text in [("hamza", base_in["body"].replace("اضافة", "إضافة").replace("الى", "إلى").replace("فى", "في")), ("currency_space", base_in["body"].replace("500EGP", "500 EGP"))]:
    clone = dict(base_in); clone.update(id="u01-"+suffix, source_message_id="masked-fixture-u01-"+suffix, body=text, variant=suffix, note="Owner-requested orthographic/spacing variation of u01."); rows.append(clone)
family(case("u02", "BankMisr", "تم تحويل مبلغ 500EGP من حساب رقم xxx1234 فى 21-Oct-2026 عن طريق التحويل اللحظي", amount=50000, direction="out", account="1234", movement="instant_transfer_out", dt="2026-10-21", precision="date", tags="instant_transfer_out|missing_merchant_allowed"))
family(case("u03", "BankMisr", "تم تحويل مبلغ 500EGP من حساب رقم xxx1234 الى كارت ائتمان / مسبق الدفع فى 21-Oct-2026", amount=50000, direction="out", account="1234", movement="possible_internal_transfer", decision="REVIEW", reasons="POSSIBLE_INTERNAL_TRANSFER", dt="2026-10-21", precision="date", tags="credit_card_funding|internal_candidate", expected_accounting_effect="pending"))
family(case("u04", "BankMisr", "عميلنا العزيز، تم إضافة الراتب لبطاقتكم اليوم. الان يمكنك الحصول على قرض شخصي خلال ساعة بدون مستندات أو مصاريف، للتقديم اضغط https://bnkmsr.com/xxxx", kind="promo", decision="IGNORE", reasons="PROMOTIONAL_MESSAGE|MISSING_AMOUNT", tags="salary_teaser|no_amount|promo"))
family(case("u05", "BankMisr", "عميلنا العزيز، استمتع بعروض كرنفال بنك مصر، واحصل على ضعف النقاط عن كل عملية شراء ببطاقات بنك مصر الائتمانية طوال فترة العرض. العرض ساري حتى 31 يوليو 2026، للمزيد https://... ، تطبق الشروط والأحكام.", kind="promo", decision="IGNORE", reasons="PROMOTIONAL_MESSAGE", tags="arabic_month|promo"))
family(case("u06", "HSBC", "From HSBC: 21OCT26 Salary to 123-456***-789 EGP 12,345.67+ Your available balance is EGP 20,000.00", amount=1234567, direction="in", balance=2000000, movement="salary", dt="2026-10-21", precision="date", tags="salary|trailing_sign|partial_account_not_last4"))
family(case("u06-transfer", "HSBC", "From HSBC: 21OCT26 Transfer to 123-456***-789 EGP 50.00+ Your available balance is EGP 20,000.00", amount=5000, direction="in", balance=2000000, movement="transfer_in", dt="2026-10-21", precision="date", evidence="u06", variant="owner_specified_descriptor", note="Owner-specified Transfer-to/50.00+ variation: ellipsis expanded using the supplied salary layout; not another independently observed full message.", tags="transfer_in|trailing_sign|missing_merchant_allowed"))
family(case("u07", "HSBC", "Your HSBC Account ********1234 was debited with IPN outward transfer for EGP 5.00 on 21-10-2026 14:30 to 5678 with reference 12fb34cc. For further details, please contact HSBC call centre", amount=500, direction="out", account="1234", movement="instant_transfer_out", dt="2026-10-21T14:30", precision="minute", expected_counterparty_token="5678", tags="ipn|counterparty_not_merchant|hex_reference"))
ipn_base = next(r for r in rows if r["id"] == "u07")
for index, ref in enumerate(["1234fc56", "1a23d4c5"], 1):
    clone = dict(ipn_base); clone.update(id=f"u07-ref{index}", source_message_id=f"masked-fixture-u07-ref{index}", body=ipn_base["body"].replace("12fb34cc", ref), variant="reference", note="Owner-requested hex-reference variant; reference is not an amount/account."); rows.append(clone)
family(case("u08", "UNSPECIFIED_WALLET", "مبروك! تم إضافة 5% كاش باك بقيمة 100 ج.م إلى محفظتك. صالح حتى 31/12/2026.", amount=10000, direction="in", movement="expiring_cashback", decision="REVIEW", reasons="COMPLETED_EVENT|PROMO_SIGNALS|EXPIRING_CREDIT", expected_expiry_date="2026-12-31", tags="cashback|expiry|percent_not_amount"))
family(case("u09", "HSBC", "From HSBC: 21OCT26 Phone Banking Transfer to 123-456***-789 EGP 12,345.67+ Your available balance is EGP 20,000.00", amount=1234567, direction="in", balance=2000000, movement="transfer_in", dt="2026-10-21", precision="date", tags="phone_banking|trailing_sign|to_not_direction"))
family(case("u10", "HSBC", "From HSBC: 21OCT26 Phone Banking Transfer from 123-456***-789 USD 1,234.56- Your available balance is USD 500.00", amount=123456, currency="USD", direction="out", balance=50000, movement="transfer_out", dt="2026-10-21", precision="date", tags="phone_banking|trailing_sign|from_not_direction"))
family(case("u12", "HSBC", "From HSBC: 21OCT26 TT Payment to 123-456***-789 USD 1,234.56+ Your available balance is USD 2,000.00", amount=123456, currency="USD", direction="in", balance=200000, movement="inward_remittance", dt="2026-10-21", precision="date", tags="tt_remittance|trailing_sign|to_not_direction"))
family(case("u13", "QNB", "IPN transfer received with amount of EGP 1234.56 on 5678 on 21/10 at 03:45 PM. Ref# 12345d67. For more details call 19700.", amount=123456, direction="in", account="5678", movement="instant_transfer_in", dt="2026-10-21T15:45", precision="minute", expected_year_origin="received_year", tags="ipn|yearless_date|12_hour_pm|phone_not_amount|hex_reference"))
qnb_in = next(r for r in rows if r["id"] == "u13")
clone = dict(qnb_in); clone.update(id="u13-masked_account", source_message_id="masked-fixture-u13-masked_account", body=qnb_in["body"].replace("on 5678", "on **5678"), variant="masked_account", note="Owner-requested masked receiving account variant."); rows.append(clone)
clone = dict(qnb_in); clone.update(id="u13-new_year", source_message_id="masked-fixture-u13-new_year", body=qnb_in["body"].replace("21/10", "31/12"), received_at_ms=int(datetime(2027,1,1,0,10,tzinfo=timezone.utc).timestamp()*1000), expected_datetime="2026-12-31T15:45", expected_year_origin="adjacent_year", variant="year_rollover", note="Received 1 January 2027, bank date 31/12: preserve 2026 event date, mark inferred year rather than fabricating a future event."); rows.append(clone)
family(case("u14", "QNB", "IPN transfer sent with amount of EGP 1234.56 from 5678 on 21/10 at 03:45 PM. Ref# 1234567b. For more details call 19700.", amount=123456, direction="out", account="5678", movement="instant_transfer_out", dt="2026-10-21T15:45", precision="minute", expected_year_origin="received_year", tags="ipn|yearless_date|12_hour_pm|phone_not_amount|hex_reference"))
qnb_out = next(r for r in rows if r["id"] == "u14")
clone = dict(qnb_out); clone.update(id="u14-truncated", source_message_id="masked-fixture-u14-truncated", body=qnb_out["body"].split(" PM.")[0], expected_datetime="2026-10-21", expected_time_precision="date", expected_time_ambiguity="missing_am_pm", variant="truncated", feature_tags="ipn|truncated|uncertain_hour|missing_reference", note="Owner-supplied truncation: amount and direction remain complete/AUTO; 03:45 lacks AM/PM, so do not assert an event hour."); rows.append(clone)
family(case("u15-maintenance", "UNSPECIFIED_EGYPTIAN_BANK", "عميلنا العزيز، يرجى العلم بأنه سيتم إجراء تحديثات على نظام بطاقات الخصم المباشر يوم الأحد الموافق 20 يوليو 2026، وذلك من الساعة 12:00 ظهراً حتى 04:00 مساءً، مما قد يؤثر على استخدام بطاقة الخصم المباشر الخاصة بكم خلال فترة التحديث، شكراً لتفهمكم.", kind="other", decision="IGNORE", reasons="NO_COMPLETED_EVENT", tags="maintenance|arabic_month|arabic_time_period"))
family(case("u15-card_policy", "UNSPECIFIED_EGYPTIAN_BANK", "عميلنا العزيز، حرصاً منا على تسهيل معاملاتكم بالبطاقات البنكية المصدرة، يرجى العلم أنه سيتم الإبقاء على بطاقة خصم فوري أصلية واحدة فقط على حساباتكم، لذا يرجى اختيار البطاقة المراد الاحتفاظ بها بالتوجه لأقرب فرع وذلك قبل (01-11-2026) ، واعتباراً من(02-11-2026) سيتم الإبقاء فقط على بطاقتكم الأصلية المنتهية بــ ( 1234) ، والبطاقات الإضافية المصدرة لها إن وجدت، للمزيد اتصل 19888", kind="other", decision="IGNORE", reasons="NO_COMPLETED_EVENT", tags="card_policy|card_digits_not_amount"))
family(case("u15-card_delivery", "UNSPECIFIED_EGYPTIAN_BANK", "عزيزي العميل، تم اصدار بطاقة الخصم المباشر وجارى تسليمها لسيادتكم من خلال شركة الشحن في محل الإقامة، ولتفعيل البطاقة ارسل اخر 4 ارقام للبطاقة ثم علامة * ثم اخر 4 ارقام من بطاقة الرقم القومي على 19700 من الرقم المحمول المسجل لدى البنك", kind="other", decision="IGNORE", reasons="NO_COMPLETED_EVENT|MISSING_AMOUNT", tags="card_delivery|completed_nonfinancial_verb"))
family(case("u15-secure_key", "HSBC", "From HSBC: 21OCT2026 , we received your HSBC secure key request. If you didn't request it, contact us immediately on the numbers at the back of your card.", kind="verification", decision="IGNORE", reasons="VERIFICATION_MESSAGE", tags="verification|secure_key_request"))

# Pairing expectations are contextual capture outcomes, not an assertion that the same isolated text is REVIEW.
for scenario, outgoing_id, gap, same_currency, review in [
    ("phone_cross_currency", "u10", 300000, False, True),
    ("phone_same_currency", "u10", 300000, True, True),
    ("phone_boundary_10m", "u10", 600000, True, True),
    ("phone_outside_10m", "u10", 600001, True, False),
]:
    outgoing = dict(next(r for r in rows if r["id"] == outgoing_id))
    incoming = dict(next(r for r in rows if r["id"] == "u09"))
    if same_currency:
        outgoing.update(body=outgoing["body"].replace("USD", "EGP"), expected_currency="EGP", expected_balance_currency="EGP")
        incoming.update(body=incoming["body"].replace("EGP 12,345.67+", "EGP 1,234.56+").replace("EGP 20,000.00", "EGP 2,000.00"), expected_amount_minor=123456, expected_balance_minor=200000)
    for offset, item in [(0, outgoing), (gap, incoming)]:
        item.update(id=scenario + ("-out" if offset == 0 else "-in"), source_message_id="fixture-"+scenario+str(offset),
                    group="context_pairing", context_group=scenario, received_at_ms=BASE+offset,
                    expected_standalone_decision="AUTO", expected_context_decision="REVIEW" if review else "AUTO",
                    expected_capture="REVIEW_INTERNAL_TRANSFER" if review else "NEW",
                    expected_capture_reason="POSSIBLE_INTERNAL_TRANSFER" if review else None,
                    expected_decision="REVIEW" if review else "AUTO", expected_accounting_effect="pending_internal" if review else None,
                    required_reasons="POSSIBLE_INTERNAL_TRANSFER" if review else item["required_reasons"],
                    variant="paired_context", feature_tags="phone_banking|internal_pairing|"+scenario,
                    note="Context fixture: standalone parse remains AUTO. Only the specified same-source Phone Banking pair within <=10 minutes is routed to REVIEW; never infer exchange rates or confirm ownership.")
        rows.append(item)

# Publicly inspectable examples. Data redaction/variable substitutions are explicitly recorded; not bank specs.
public = [
    case("pub-cib-ar", "CIB", "تم خصم مبلغ  EGP 160.00 من بطاقة الخصم المباشر المنتهية بـ **1234 عند SAMPLE COFFEE STORE في 21/10/26 00:27 ، الرصيد المتاح 9000.18 EGP", group="public_documented", amount=16000, direction="out", merchant="sample coffee store", balance=900018, card="1234", movement="card_purchase", dt="2026-10-21T00:27", precision="minute", evidence="public-cib-issue145", tags="public_card_purchase|arabic_cib"),
    case("pub-cib-charge", "CIB", "Your credit card ending with#1234 was charged for EGP 118.00 at SAMPLE MARKET on 21/10/26  at 18:27. Card available limit is EGP  10000.21. For more details, please visit https://cib.eg/mb", group="public_documented", amount=11800, direction="out", merchant="sample market", card="1234", movement="card_purchase", dt="2026-10-21T18:27", precision="minute", evidence="public-cib-test", expected_credit_limit_minor=1000021, expected_credit_limit_currency="EGP", tags="public_card_purchase|credit_limit_not_balance"),
    case("pub-cib-refund", "CIB", "The transaction on your credit card#1234  from SAMPLE CLOUD  with EUR .93 on 21/10/26  at 05:14 has been refunded. Please try again. Thank you", group="public_documented", amount=93, currency="EUR", direction="in", merchant="sample cloud", card="1234", movement="refund", dt="2026-10-21T05:14", precision="minute", evidence="public-cib-test", tags="refund|leading_decimal|completed_credit_with_instruction_footer"),
    case("pub-nbe-debit", "BanK-AlAhly", "تم خصم 200 EGP من بطاقة الخصم المباشر رقم2222 عندNBE ATM546 يوم03/09/26 الساعة21:13 المتاح800.00EGP للمزيد اتصل ب 10000", group="public_documented", amount=20000, direction="out", merchant="nbe atm546", card="2222", balance=80000, movement="cash_withdrawal", dt="2026-09-03T21:13", precision="minute", evidence="public-nbe-test", tags="public_card_withdrawal|no_spaces|not_everyday_spending"),
    case("pub-nbe-card", "BanK-AlAhly", "تم خصم 100.50 جم من بطاقة الائتمان رقم 1111 عند KASHIERFast يوم 09-10 الساعة 10:11 المتاح 9000.00 جم للمزيد اتصل ب 10000.", group="public_documented", amount=10050, direction="out", merchant="kashierfast", card="1111", movement="card_purchase", evidence="public-nbe-test", expected_credit_limit_minor=900000, expected_credit_limit_currency="EGP", expected_time_ambiguity="ambiguous_day_month_missing_year", tags="public_card_purchase|credit_limit_not_balance|no_processor_delimiter"),
    case("pub-nbe-instant", "BanK-AlAhly", "تم إضافة تحويل لحظي لحسابكم رقم 4444 بمبلغ 400.00 جم من اسم تجريبي رقم مرجعي 123456789012 يوم 09-10 الساعة 20:11 للمزيد اتصل بـ 10000", group="public_documented", amount=40000, direction="in", merchant="اسم تجريبي", account="4444", movement="instant_transfer_in", evidence="public-nbe-test", expected_time_ambiguity="ambiguous_day_month_missing_year", tags="instant_transfer_in|partial_date|public_fixture"),
    case("pub-cib-ipn", "CIB", "IPN transfer received with amount of EGP 450.00 on 1234 on 21/10 at 09:19 PM. Ref# ab2c3dd4. For more details call 19700.", group="public_documented", amount=45000, direction="in", account="1234", movement="instant_transfer_in", dt="2026-10-21T21:19", precision="minute", expected_year_origin="received_year", evidence="public-cib-ipn-readme", tags="ipn|public_example|12_hour_pm"),
]
for row in public:
    row["note"] = "Public example with example amounts/dates/identifiers/names substituted. Evidence level is recorded; not represented as a bank-issued SMS specification."
    family(row, count=2)

clone = dict(base_in); clone.update(id="u01-arabic_month", source_message_id="masked-fixture-u01-arabic_month", body=base_in["body"].replace("21-Oct-2026", "21 أكتوبر 2026"), variant="arabic_month", feature_tags="instant_transfer_in|arabic_month", note="Declared generated date-spelling variation of user example u01, not an additional observed bank form."); rows.append(clone)
clone = dict(ipn_base); clone.update(id="u07-arabic_pm", source_message_id="masked-fixture-u07-arabic_pm", body=ipn_base["body"].replace("14:30", "٠٢:٣٠ م"), variant="arabic_pm", feature_tags="ipn|arabic_pm", note="Declared generated equivalent 12-hour clock variation of user example u07; expected 14:30, not a newly observed bank form."); rows.append(clone)


def measured_language(row):
    text = row["body"] if row["channel"] == "sms" else "\n".join(row[col] or "" for col in ORIGINAL if col.startswith("android."))
    text = re.sub(r"(?i)(?:https?://|www\.)[^\s]+", " ", text)
    text = re.sub(r"(?i)\d{1,2}[- /]?(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)[- /]?\d{2,4}", " ", text)
    text = re.sub(r"(?i)\d+:\d+(?::\d+)?(?:\s*(?:am|pm|ص|م))?", " ", text)
    text = re.sub(r"(?i)(?<![a-z])(?:egp|usd|eur|gbp|sar|aed|kwd|le)(?![a-z])", " ", text)
    text = re.sub(r"(?i)\bx+[\d*#-]+", " ", text)
    scripts = {unicodedata.name(ch, "").split(" ")[0] for ch in text if ch.isalpha()}
    return "mixed" if {"ARABIC", "LATIN"} <= scripts else "ar" if "ARABIC" in scripts else "en" if "LATIN" in scripts else "neutral"

for row in rows:
    if row["group"] not in FROZEN:
        row["language"] = measured_language(row)

for row in rows:
    if row["group"] == "synthetic_coverage":
        assert row["source"] == row["source_evidence_id"] and row["note"].startswith("SYNTHETIC"), row["id"]
assert len({r["id"] for r in rows}) == len(rows)
frozen_original = {r["id"]: r for r in v1 if r["group"] in FROZEN}
for row in rows:
    if row["id"] in frozen_original:
        assert all(row[col] == frozen_original[row["id"]][col] for col in ORIGINAL)

dest = FIXTURES / "parser-corpus-v2-draft.tsv"
dest.write_text("\t".join(COLUMNS) + "\n" + "\n".join("\t".join(encode(row.get(col)) for col in COLUMNS) for row in rows) + "\n", encoding="utf-8", newline="\n")
print(f"Wrote {len(rows)} v2 DRAFT rows; {len(frozen_original)} protected v1 rows unchanged; source_independence removed.")
print("Decisions:", dict(Counter(r["expected_decision"] for r in rows)))
print("Types:", dict(Counter(r["expected_type"] for r in rows)))
print("Measured languages:", dict(Counter(measured_language(r) for r in rows)))
tx = [r for r in rows if r["expected_type"] == "transaction"]
print("Transaction currencies:", dict(Counter(r["expected_currency"] for r in tx)))
print("Language ratios are measured from message words, excluding URLs, date tokens, ISO currency codes and masked xxx accounts; preserved v1 labels are not quota evidence.")
print("Evidence groups:", dict(Counter(r["group"] for r in rows if r["group"] in {"user_masked", "public_documented", "synthetic_coverage"})))
print("NOT APPROVED: message-type coverage and distribution gates are reported by the v2 audit.")
