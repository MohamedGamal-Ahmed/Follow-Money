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
source_evidence_id base_case_id variant feature_tags expected_accounting_effect
expected_original_amount_minor expected_original_currency""".split()
FROZEN = {"notification_fields", "dedupe_identity", "promotions", "other_and_failed"}
# Protected-row cells the owner explicitly changed (21 Oct 2026); every other protected cell stays byte-identical to v1.
OWNER_EXCEPTIONS = {("p011", "expected_card_last4")}
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
    elif row["id"] in {"p011", "p146"}:
        # Owner decision 21 Oct 2026 (explicit exception to v1 protection): an account mask is an account, not a card.
        row.update(expected_account_last4=row["expected_card_last4"], expected_card_last4=None)
        if row["id"] == "p146":  # p011 is a protected row: only its card cell is excepted, so its note stays as in v1.
            row["note"] += " Owner-corrected v2 oracle: account mask moved from card_last4 to account_last4."
    elif row["id"] == "p136":
        row.update(expected_currency=None,
                   note=row["note"] + " Owner-corrected v2 oracle: a two-event message has no amount and no currency, like p014/p135.")
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
    case("pub-cib-refund", "CIB", "The transaction on your credit card#1234  from SAMPLE CLOUD  with EUR .93 on 21/10/26  at 05:14 has been refunded. Please try again. Thank you", group="public_documented", amount=93, currency="EUR", direction="in", merchant="sample cloud", card="1234", movement="refund", expected_accounting_effect="expense_offset", dt="2026-10-21T05:14", precision="minute", evidence="public-cib-test", tags="refund|expense_offset|leading_decimal|completed_credit_with_instruction_footer"),
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

# Synthetic message-type coverage (owner policy, 21 October 2026). Each base is labelled SYNTHETIC and attributed to
# PUBLIC_FORMAT_<bank> or SYNTHETIC_<type>; none is an observed message or a format claim for any real sender.
FEE = "COMPLETED_EVENT|AMOUNT_UNAMBIGUOUS|DIRECTION_OUT|EXPLICIT_FEE"
DECLINED = dict(kind="other", currency=None, decision="IGNORE", reasons="UNSUCCESSFUL_EVENT")
OWN_STRUCTURE = "Built on the structure of our observed messages; wording, amounts, dates and names are invented."
family(synthetic("syn-ipnfee-ar", "SYNTHETIC_IPN_FEE", "تم تحويل مبلغ 1,000.00 ج.م عبر التحويل اللحظي إلى حساب رقم ****9012 بتاريخ 21/10/2026 الساعة 13:05. رسوم الخدمة 5.00 ج.م. الرقم المرجعي 778899.", amount=100000, direction="out", movement="instant_transfer_out", expected_counterparty_token="9012", expected_fee_minor=500, expected_fee_currency="EGP", reasons=FEE, dt="2026-10-21T13:05", precision="minute", tags="ipn_explicit_fee|instant_transfer_out|fee_not_principal|reference_not_amount", note="Instant transfer stating its own fee: principal 1,000 EGP, separate fee 5 EGP, never summed. " + OWN_STRUCTURE))
family(synthetic("syn-ipnfee-en", "SYNTHETIC_IPN_FEE", "InstaPay transfer of EGP 2,500.00 to account ****3456 completed on 21/10/2026 at 10:15 AM. Fee: EGP 5.00. Ref 9f3a21b7.", amount=250000, direction="out", movement="instant_transfer_out", expected_counterparty_token="3456", expected_fee_minor=500, expected_fee_currency="EGP", reasons=FEE, dt="2026-10-21T10:15", precision="minute", tags="ipn_explicit_fee|instant_transfer_out|fee_not_principal|hex_reference", note="English instant transfer with explicit fee; principal 2,500 EGP, fee 5 EGP. " + OWN_STRUCTURE), count=2)
family(synthetic("syn-cib-dear-en", "PUBLIC_FORMAT_CIB", "Dear Customer, your credit card ending with#1234 was charged for EGP 245.50 at SAMPLE PHARMACY on 21/10/26  at 19:42. Card available limit is EGP  8000.00. For more details, please visit https://cib.eg/mb", amount=24550, direction="out", merchant="sample pharmacy", card="1234", movement="card_purchase", expected_credit_limit_minor=800000, expected_credit_limit_currency="EGP", dt="2026-10-21T19:42", precision="minute", tags="cib_dear_customer_purchase|card_purchase|greeting_prefix|credit_limit_not_balance", note="Dear Customer greeting prepended to the published CIB credit-card charge structure; not an observed CIB message."), count=2)
family(synthetic("syn-cib-dear-ar", "PUBLIC_FORMAT_CIB", "عزيزي العميل، تم خصم مبلغ  EGP 160.00 من بطاقة الخصم المباشر المنتهية بـ **1234 عند صيدلية النور في 21/10/26 00:27 ، الرصيد المتاح 9000.18 EGP", amount=16000, direction="out", merchant="صيدليه النور", card="1234", balance=900018, movement="card_purchase", dt="2026-10-21T00:27", precision="minute", tags="cib_dear_customer_purchase|card_purchase|greeting_prefix|arabic_merchant", note="عزيزي العميل greeting prepended to the published Arabic CIB debit-card structure (public-cib-issue145); not an observed CIB message."))
family(synthetic("syn-nbe-dear-credit", "PUBLIC_FORMAT_NBE", "عزيزي العميل، تم خصم 350.00 جم من بطاقة الائتمان رقم 1111 عند هايبر وان يوم 21/10/26 الساعة 18:20 المتاح 7500.00 جم للمزيد اتصل ب 10000", amount=35000, direction="out", merchant="هايبر وان", card="1111", movement="card_purchase", expected_credit_limit_minor=750000, expected_credit_limit_currency="EGP", dt="2026-10-21T18:20", precision="minute", tags="nbe_dear_customer_purchase|card_purchase|greeting_prefix|credit_limit_not_balance|phone_not_amount", note="عزيزي العميل greeting prepended to the published NBE credit-card spend structure; not an observed NBE message."))
family(synthetic("syn-nbe-dear-debit", "PUBLIC_FORMAT_NBE", "عزيزي العميل، تم خصم 85.00 جم من بطاقة الخصم المباشر رقم 2222 عند مخبز الأمانة يوم 21/10/26 الساعة 08:05 المتاح 3400.00 جم للمزيد اتصل ب 10000", amount=8500, direction="out", merchant="مخبز الامانه", card="2222", balance=340000, movement="card_purchase", dt="2026-10-21T08:05", precision="minute", tags="nbe_dear_customer_purchase|card_purchase|greeting_prefix|debit_balance|phone_not_amount", note="عزيزي العميل greeting prepended to the published NBE debit-card structure; debit-card available amount is a balance. Not an observed NBE message."))
family(synthetic("syn-dual-en", "SYNTHETIC_DUAL_CURRENCY", "Your card ending 4321 was charged USD 9.99 (EGP 495.25 equivalent) at GOOGLE PLAY on 21/10/2026 11:20.", amount=49525, currency="EGP", direction="out", merchant="google play", card="4321", movement="card_purchase", expected_original_amount_minor=999, expected_original_currency="USD", dt="2026-10-21T11:20", precision="minute", tags="dual_currency_equivalent|account_currency_amount|original_foreign_amount", note="Owner decision: the accounting amount is the account-currency equivalent EGP 495.25 (what left the account and budget); USD 9.99 is kept as original_amount/original_currency for display only. " + OWN_STRUCTURE), count=2)
family(synthetic("syn-dual-ar", "SYNTHETIC_DUAL_CURRENCY", "تم خصم 9.99 دولار أمريكي (ما يعادل 495.25 جنيه) من بطاقتك المنتهية بـ 4321 لدى جوجل بلاي يوم 21/10/2026 الساعة 11:20", amount=49525, currency="EGP", direction="out", merchant="جوجل بلاي", card="4321", movement="card_purchase", expected_original_amount_minor=999, expected_original_currency="USD", dt="2026-10-21T11:20", precision="minute", tags="dual_currency_equivalent|account_currency_amount|original_foreign_amount|arabic_currency_words", note="Arabic dual-currency charge: accounting amount EGP 495.25 (account currency); USD 9.99 is the original amount for display. " + OWN_STRUCTURE))
family(synthetic("syn-reversal-ar", "SYNTHETIC_REVERSAL", "تم عكس عملية خصم بمبلغ 250.00 جنيه على بطاقتكم المنتهية بـ 5566 لدى متجر الهدى بتاريخ 21/10/2026، وتمت إعادة المبلغ إلى حسابكم. الرصيد المتاح 4,750.00 جنيه", amount=25000, direction="in", merchant="متجر الهدي", card="5566", balance=475000, movement="reversal", expected_accounting_effect="expense_offset", dt="2026-10-21", precision="date", tags="reversal|expense_offset|debit_word_in_credit_event", note="Reversal credits the earlier debit back; the word خصم inside it does not make it outgoing. Distinct from a merchant refund. " + OWN_STRUCTURE))
family(synthetic("syn-reversal-atm-ar", "SYNTHETIC_REVERSAL", "تم إلغاء عملية سحب نقدي بمبلغ 500.00 جنيه من ماكينة الصراف الآلي وإعادة المبلغ لحسابكم المنتهي بـ 6789 بتاريخ 21/10/2026", amount=50000, direction="in", account="6789", movement="reversal", expected_accounting_effect="expense_offset", dt="2026-10-21", precision="date", tags="reversal|expense_offset|atm_reversal|cancelled_withdrawal_credit", note="Cancelled ATM withdrawal with the amount returned: an incoming reversal, not a withdrawal or failed operation. " + OWN_STRUCTURE))
family(synthetic("syn-reversal-en", "SYNTHETIC_REVERSAL", "Reversal: EGP 250.00 has been credited back to your card ending 5566 for the transaction at SAMPLE STORE on 21/10/2026.", amount=25000, direction="in", merchant="sample store", card="5566", movement="reversal", expected_accounting_effect="expense_offset", dt="2026-10-21", precision="date", tags="reversal|expense_offset|credited_back", note="English reversal credit. " + OWN_STRUCTURE), count=2)
family(synthetic("syn-installment-ar", "SYNTHETIC_INSTALLMENT", "تم سداد القسط رقم 3 من 12 بقيمة 1,250.00 جنيه لخطة تقسيط مشترياتك على بطاقتكم المنتهية بـ 7788 بتاريخ 21/10/2026. المتبقي 9 أقساط.", amount=125000, direction="out", card="7788", movement="installment_payment", dt="2026-10-21", precision="date", tags="installment_payment|installment_counter_not_amount", note="Completed installment payment; 3, 12 and 9 are installment counters, not amounts. " + OWN_STRUCTURE))
family(synthetic("syn-installment-en", "SYNTHETIC_INSTALLMENT", "Installment 3/12 of EGP 1,250.00 for your purchase plan has been debited from account ****7788 on 21/10/2026.", amount=125000, direction="out", account="7788", movement="installment_payment", dt="2026-10-21", precision="date", tags="installment_payment|counter_looks_like_date", note="3/12 is an installment counter, not a date or amount. " + OWN_STRUCTURE), count=2)
family(synthetic("syn-topup-ar", "SYNTHETIC_WALLET_TOPUP", "تم شحن محفظتك بمبلغ 300.00 جنيه من بطاقتك المنتهية بـ 4455 بتاريخ 21/10/2026 الساعة 16:40. رصيد المحفظة 1,150.00 جنيه.", amount=30000, direction="in", card="4455", balance=115000, movement="wallet_topup", decision="REVIEW", reasons="COMPLETED_EVENT|AMOUNT_UNAMBIGUOUS|DIRECTION_IN|POSSIBLE_INTERNAL_TRANSFER", expected_accounting_effect="pending_internal", dt="2026-10-21T16:40", precision="minute", tags="wallet_topup|internal_candidate|wallet_balance", note="Wallet top-up from the user's own card is a possible internal transfer: REVIEW/pending, not income. " + OWN_STRUCTURE))
family(synthetic("syn-fawry-ar", "SYNTHETIC_FAWRY_BILL", "تم دفع فاتورة الكهرباء بمبلغ 435.75 جنيه عن طريق فوري. رقم العملية 556677889. شكرا لاستخدامك فوري.", amount=43575, direction="out", merchant="الكهرباء", movement="bill_payment", tags="fawry_bill_payment|bill_payment|reference_not_amount", note="Fawry bill payment; the biller is the merchant and the operation number is a reference. " + OWN_STRUCTURE))
family(synthetic("syn-fawry-recharge-ar", "SYNTHETIC_FAWRY_BILL", "تم شحن رصيد اتصالات بقيمة 50.00 جنيه للرقم 011XXXXX789 عن طريق فوري. رسوم الخدمة 1.50 جنيه.", amount=5000, direction="out", merchant="اتصالات", movement="bill_payment", expected_fee_minor=150, expected_fee_currency="EGP", reasons=FEE, tags="fawry_bill_payment|mobile_recharge|topup_word_not_wallet|fee_not_principal", note="Mobile recharge paid through Fawry: outgoing service payment despite the word شحن; fee stays separate. " + OWN_STRUCTURE))
family(synthetic("syn-fawry-en", "SYNTHETIC_FAWRY_BILL", "Fawry: Payment of EGP 220.00 for Vodafone mobile bill 010XXXXXX45 was successful. Service fee EGP 2.50. Ref 778812345. 21/10/2026 09:12", amount=22000, direction="out", merchant="vodafone", movement="bill_payment", expected_fee_minor=250, expected_fee_currency="EGP", reasons=FEE, dt="2026-10-21T09:12", precision="minute", tags="fawry_bill_payment|bill_payment|fee_not_principal|operator_not_wallet", note="English Fawry bill payment with explicit service fee; a Vodafone bill is not Vodafone Cash. " + OWN_STRUCTURE), count=2)
family(synthetic("syn-decline-ar", "SYNTHETIC_CARD_DECLINE", "عذرا، تم رفض عملية شراء بمبلغ 1,200.00 جنيه على بطاقتكم المنتهية بـ 3344 لدى متجر الأمل بتاريخ 21/10/2026 بسبب عدم كفاية الرصيد.", tags="card_decline|unsuccessful|completed_verb_negated_by_decline", note="Declined purchase: no transaction despite amount, card and merchant. " + OWN_STRUCTURE, **DECLINED))
family(synthetic("syn-decline-limit-ar", "SYNTHETIC_CARD_DECLINE", "العملية مرفوضة: لم تتم عملية الشراء بقيمة 75.00 جنيه لدى مطعم السلطان باستخدام بطاقتكم المنتهية بـ 3344 لتجاوز الحد اليومي.", tags="card_decline|unsuccessful|limit_exceeded", note="Declined for daily limit: no transaction. " + OWN_STRUCTURE, **DECLINED))
family(synthetic("syn-decline-en", "SYNTHETIC_CARD_DECLINE", "Your card ending 3344 was declined for EGP 1,200.00 at SAMPLE ELECTRONICS on 21/10/2026 14:02 due to insufficient funds.", tags="card_decline|unsuccessful", note="English card decline: no transaction. " + OWN_STRUCTURE, **DECLINED), count=2)
family(synthetic("syn-vfcash-pay", "SYNTHETIC_VODAFONE_CASH", "تم دفع 150.00 جنيه لـ سوبر ماركت الرحمة من محفظة فودافون كاش. رصيدك الحالي 850.00 جنيه. رقم العملية 123456789.", amount=15000, direction="out", merchant="سوبر ماركت الرحمه", balance=85000, movement="wallet_payment", tags="vodafone_cash|wallet_payment|wallet_balance|reference_not_amount", note="Vodafone Cash wallet debit/payment. " + OWN_STRUCTURE))
family(synthetic("syn-vfcash-in", "SYNTHETIC_VODAFONE_CASH", "تم استلام 500.00 جنيه من رقم 010XXXXX123 على محفظة فودافون كاش. رصيدك الحالي 1,350.00 جنيه.", amount=50000, direction="in", balance=135000, movement="wallet_transfer_in", expected_counterparty_token="010XXXXX123", tags="vodafone_cash|wallet_transfer_in|missing_merchant_allowed|masked_phone", note="Vodafone Cash deposit from another number; missing merchant is not a REVIEW reason for a wallet transfer. " + OWN_STRUCTURE), count=2)
family(synthetic("syn-vfcash-out", "SYNTHETIC_VODAFONE_CASH", "تم تحويل 200.00 جنيه من محفظة فودافون كاش إلى رقم 011XXXXX456. رسوم الخدمة 1.00 جنيه. رصيدك الحالي 649.00 جنيه.", amount=20000, direction="out", balance=64900, movement="wallet_transfer_out", expected_fee_minor=100, expected_fee_currency="EGP", reasons=FEE, tags="vodafone_cash|wallet_transfer_out|fee_not_principal|missing_merchant_allowed", note="Vodafone Cash transfer out; principal 200 EGP, fee 1 EGP, balance after both. " + OWN_STRUCTURE), count=2)


def notification_family(row, title, text, big=None):
    """Direct app notification: separate Android extras, base plus digit and spacing variants."""
    row.update(channel="notification", body=None, source_message_id=None, notification_key="0|synthetic.instapay|" + row["id"] + "|fixture|10000",
               **{"android.title": title, "android.text": text, "android.bigText": big})
    rows.append(row)
    for name, transform in [("digits", lambda t: t.translate(str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩"))),
                            ("spaces", lambda t: "‏" + t.replace(" ", "   "))]:
        clone = dict(row)
        clone.update(id=row["id"] + "-" + name, notification_key=row["notification_key"] + "-" + name, variant=name,
                     note="SYNTHETIC: Generated " + name + " variation of " + row["id"] + "; not another observed source format.",
                     **{field: transform(row[field]) for field in ("android.text", "android.bigText") if row[field]})
        rows.append(clone)


notification_family(synthetic("syn-instapay-ar", "SYNTHETIC_INSTAPAY_DIRECT", None, amount=75000, direction="out", movement="instant_transfer_out", expected_counterparty_token="ahmed.s@instapay", dt="2026-10-21T12:30", precision="minute", tags="instapay_direct|notification_extras|instant_transfer_out|missing_merchant_allowed", note="Direct InstaPay app notification with title/text/bigText extras; payment address is a counterparty, not a merchant. " + OWN_STRUCTURE),
                    "إنستاباي", "تم إرسال 750.00 جنيه إلى ahmed.s@instapay بنجاح", "تم إرسال 750.00 جنيه إلى ahmed.s@instapay بنجاح بتاريخ 21/10/2026 الساعة 12:30. الرقم المرجعي 44556677.")
notification_family(synthetic("syn-instapay-en", "SYNTHETIC_INSTAPAY_DIRECT", None, amount=100000, direction="in", movement="instant_transfer_in", expected_counterparty_token="sara.m@instapay", tags="instapay_direct|notification_extras|instant_transfer_in|missing_merchant_allowed", note="English InstaPay received notification; text only, no bank-supplied time. " + OWN_STRUCTURE),
                    "InstaPay", "You have received EGP 1,000.00 from sara.m@instapay")


# Owner phone samples (21 October 2026, user_redacted). Appended last so earlier rows keep their receipt times.
BM_PURCHASE = ("شكرًا لاستخدامك بطاقة بنك مصر ***4636، تم الآن خصم EGP 15.00عند KASHIER*BUTLERS & CO C يوم 20/10/2026 ، "
               "الرصيد المتاح EGP 2139.44 لمزيد من المعلومات عن الحساب، تفضل بزيارة الرابط التالي https://bnkmsr.com/online")
family(case("bm01", "BankMisr", BM_PURCHASE, amount=1500, direction="out", merchant="butlers", card="4636", balance=213944,
            movement="card_purchase", dt="2026-10-20", precision="date", tags="card_purchase|glued_word_after_amount|processor_prefix|truncated_terminal_name",
            note="Owner phone sample: the word عند is glued to the amount in every Bank Misr purchase (96 messages). Amount keeps its piasters; KASHIER* is a processor prefix; '& CO C' is a fixed-width terminal name cut off."))
bm_base = next(r for r in rows if r["id"] == "bm01")
for suffix, old, new, minor, key, movement in [
    ("seoudi", "15.00عند KASHIER*BUTLERS & CO C", "44.95عند SEOUDI SODIC EAST", 4495, "seoudi sodic east", "card_purchase"),
    ("zaher", "15.00عند KASHIER*BUTLERS & CO C", "1897.71عند ZAHER-AIN SHAMS 1", 189771, "zaher ain shams 1", "card_purchase"),
    ("vodafone", "15.00عند KASHIER*BUTLERS & CO C", "490.00عند Bill Payment Vodafone", 49000, "vodafone", "bill_payment"),
]:
    clone = dict(bm_base); clone.update(id="bm01-" + suffix, source_message_id="masked-fixture-bm01-" + suffix, body=bm_base["body"].replace(old, new),
        expected_amount_minor=minor, expected_merchant_key=key, expected_movement_kind=movement, variant="amount_merchant",
        note="Owner-specified amount/merchant variant of bm01 (same Bank Misr form). ZAHER: the branch number 1 is kept, as for any branch number alone; Bill Payment is a descriptor before the biller.")
    rows.append(clone)
# Glued shapes (owner rule): a number ends at the first character that is neither a digit nor a separator before a digit.
for suffix, old, new, minor in [
    ("glued_integer", "EGP 15.00عند", "EGP 15عند", 1500),
    ("currency_after_fraction", "EGP 15.00عند", "15.00EGP عند", 1500),
    ("currency_before_glued", "EGP 15.00عند", "EGP15.00عند", 1500),
    ("arabic_currency_after", "EGP 15.00عند", "15.00ج.م عند", 1500),
    # Observed on the owner's phone (79 Bank Misr purchases, masked shapes): the currency follows the amount and عند is glued to it.
    ("currency_after_glued_word", "EGP 15.00عند", "15.00 EGPعند", 1500),
]:
    clone = dict(bm_base); clone.update(id="bm01-" + suffix, source_message_id="masked-fixture-bm01-" + suffix, body=bm_base["body"].replace(old, new),
        expected_amount_minor=minor, variant=suffix, feature_tags="card_purchase|glued_amount|" + suffix,
        note="Declared glued-amount variation of bm01 (owner rule: no fraction may be lost and a glued word or currency is a separate word); not another observed form.")
    rows.append(clone)
family(case("bm02", "BankMisr", "عميلنا العزيز الحركة التي تمت ببطاقتكم ***4636 بمبلغ EGP 15.00 لدى KASHIER*BUTLERS & CO C هي اخر حركة لا تلامسية ، يرجى استخدام حركة المشتريات القادمة باستخدام الرقم السري حتى يمكن إعادة استخدام خاصية اللا تلامسية مرة أخرى",
            kind="other", decision="IGNORE", reasons="NO_NEW_MOVEMENT", tags="past_transaction_reference|instruction|no_new_movement",
            note="Owner phone sample: refers to an earlier contactless purchase and gives instructions; no new money moves."))
for identifier, text, minor, balance in [
    ("we01", "تم شحن رصيدك على الرقم 0224994891 ب 136.80 ج.م. إجمالي رصيدك 120.00 ج.م.", 13680, 12000),
    ("we02", "تم شحن الإنترنت الأرضي 0224994891 ب 450.30 ج م. رصيدك 400.01 ج م.", 45030, 40001),
]:
    family(case(identifier, "we-Landline", text, amount=minor, direction="out", balance=balance, movement="service_topup", decision="REVIEW",
                reasons="COMPLETED_EVENT|AMOUNT_UNAMBIGUOUS|DIRECTION_OUT|PAYER_UNKNOWN", tags="service_topup|provider_confirmation|payer_unknown|topup_word_not_balance",
                note="Owner phone sample: the service provider confirms a top-up of the user's line without naming the paying account, so the same payment may already be recorded from the bank or wallet message (PAYER_UNKNOWN, REVIEW). The stated balance is the line's balance."))


# Owner phone groups (21 October 2026): forms read as masked shapes on the phone (digits and names hidden). Text
# rebuilt from the shape with invented values and names; the template words are the observed ones.
SHAPE_NOTE = "Rebuilt from a masked shape read on the owner's phone; values and names invented, template words observed. "
family(case("bm03", "BankMisr", "شكرا لاستخدامك بطاقة بنك مصر*1234 ، تم الخصم الان من رصيدك مبلغ EGP 175.88 علي CARREFOUR MAADI يوم 22/07/2026 ، الرصيد المتاح EGP 2000.12. لمزيد من المعلومات عن الحساب، تفضل بزيارة الرابط التالي https://bnkmsr.com/online",
            amount=17588, direction="out", merchant="carrefour maadi", card="1234", balance=200012, movement="card_purchase", dt="2026-07-22", precision="date",
            tags="card_purchase|debit_from_balance_wording|merchant_after_ala",
            note=SHAPE_NOTE + "40 messages: a real card debit; 'من رصيدك' names where the money came from, not a balance message. No earlier message repeats the amount (no double count)."))
family(case("bm04", "BankMisr", "تم تنفيذ تحويل لحظي من بطاقة رقم 1234 بمبلغ 2000 رقم مرجعي 123456789يوم 24/07/2026 الساعه 20:53 للمزيد اتصل علي 19888",
            amount=200000, direction="out", card="1234", movement="instant_transfer_out", decision="REVIEW", reasons="COMPLETED_EVENT|AMOUNT_UNAMBIGUOUS|DIRECTION_OUT|CURRENCY_INFERRED",
            dt="2026-07-24T20:53", precision="minute", tags="instant_transfer_out|labelled_amount_without_currency|currency_inferred",
            note=SHAPE_NOTE + "43 messages: the amount is labelled (بمبلغ) but carries no currency anywhere in the message; the account currency is inferred and the user confirms it (CURRENCY_INFERRED, learnable)."))
family(case("bm05", "BankMisr", "شكرا لاستخدامك بطاقة بنك مصر ***1234 ، تم الخصم مبلغEGP 500.00 الة رقم 12345 BM ATM MAADI يوم 24/07/2026 ، الرصيد المتاحEGP 1500.00لمزيد من المعلومات اضغط هنا https://bnkmsr.com/x",
            amount=50000, direction="out", card="1234", balance=150000, movement="cash_withdrawal", dt="2026-07-24", precision="date",
            tags="cash_withdrawal|currency_glued_after_arabic_word|atm_machine_number",
            note=SHAPE_NOTE + "10 messages: EGP glued to the Arabic word before the amount; آلة رقم is a cash machine."))
family(case("bm06", "BankMisr", "شكرا لاستخدامكم بطاقة بنك مصر الائتمانية ***1234 تم ايداع EGP 8431.05 في CARD PAYMENT يوم 28/03/2025 متاح الان EGP 8617.83 للمزيد من المعلومات اتصل 19888",
            amount=843105, direction="in", card="1234", dt="2025-03-28", precision="date", movement=None,
            expected_credit_limit_minor=861783, expected_credit_limit_currency="EGP", tags="credit_card_deposit|available_now_is_limit",
            note=SHAPE_NOTE + "11 messages: the deposit is the first amount; 'متاح الان' on a credit card is the available limit (legacy took it as the income)."))
family(case("op01", "7030", "Dear Customer, You have successfully renewed 1 GB subscription - 1 month (Super Plan) from Orange and 39 Egyptian pound has been deducted from your line",
            amount=3900, direction="out", merchant="orange", movement="service_charge", decision="REVIEW", reasons="COMPLETED_EVENT|AMOUNT_UNAMBIGUOUS|DIRECTION_OUT|PREPAID_CREDIT",
            tags="service_charge|prepaid_credit|egyptian_pound|operator_subscription",
            note=SHAPE_NOTE + "65 messages: a subscription paid from the line's prepaid credit; recording it after the line top-up may double count, so it waits for the owner (PREPAID_CREDIT)."))
for identifier, source, text in [
    ("pc01", "Premiumcard", "لقد تم السداد للقسط الشهري الخاص ببريميوم كارد بقيمة 245.00 جنيه القيمة المستحقة علي حسابك الان هي 1200.00 جنيه"),
    ("tru01", "TRU", "عميل ترو العزيز، تمت عملية الشراء من عند BTECH 12 بنجاح في 26/09/2026. اجمالي المبلغ الممول 6636.00، قسطك بقيمة 553.00 شهريا، لمدة 12 شهر."),
]:
    family(case(identifier, source, text, kind="other", decision="IGNORE", reasons="COMMITMENT_PATH", tags="installment_provider|commitment_path",
                note=SHAPE_NOTE + "Owner decision: an installment message from the provider that names no debited account, card or wallet belongs to the commitments path, not the ledger."))


family(case("bm07", "BankMisr", "Deposit of EGP 60.00 was made to your credit card ending 1234 on 01/05/2025.Your current balance is EGP 940.00.To know more, visit bnkmsr.com/online.",
            amount=6000, direction="in", card="1234", movement="possible_internal_transfer", decision="REVIEW", reasons="COMPLETED_EVENT|AMOUNT_UNAMBIGUOUS|DIRECTION_IN|POSSIBLE_INTERNAL_TRANSFER",
            expected_credit_limit_minor=94000, expected_credit_limit_currency="EGP", dt="2025-05-01", precision="date", expected_accounting_effect="pending",
            tags="credit_card_repayment|deposit_of|internal_candidate",
            note=SHAPE_NOTE + "3 messages: a deposit onto the user's credit card is incoming and possibly from the user's own account (card repayment); legacy read it as income."), count=2)
family(case("bdc01", "BDC", "Thank you for your payment of EGP 15.00 to your card ****5678",
            amount=1500, direction="out", card="5678", movement="possible_internal_transfer", decision="REVIEW", reasons="COMPLETED_EVENT|AMOUNT_UNAMBIGUOUS|DIRECTION_OUT|POSSIBLE_INTERNAL_TRANSFER",
            expected_accounting_effect="pending", tags="credit_card_repayment|thank_you_for_payment|internal_candidate",
            note=SHAPE_NOTE + "A payment to the user's own card: completed, possibly between own accounts; 'for your' after 'thank you' is courtesy, not a reference."), count=2)
family(case("pc02", "Premiumcard", "مبروك تم تفعيل كارت بريميوم الخاص بك ب حد ائتماني 20000 جنيه، يمكنك البدء في التسوق. معلومات عن بطاقتك: نوع البطاقة : جولد رسوم التجديد السنوية: 240 جنيه مصاريف ادارية ثابته: 7000 جنيه لكل عملية شراء",
            kind="other", decision="IGNORE", reasons="NO_NEW_MOVEMENT", tags="card_activation|fees_listed_not_charged|no_new_movement",
            note=SHAPE_NOTE + "A card activation notice that lists the limit and fees; no money moved (legacy recorded the fee figure)."))


def measured_language(row):
    text = row["body"] if row["channel"] == "sms" else "\n".join(row[col] or "" for col in ORIGINAL if col.startswith("android."))
    text = text.translate(str.maketrans("٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹", "01234567890123456789"))
    text = re.sub(r"(?i)(?:https?://|www\.)[^\s]+", " ", text)
    text = re.sub(r"(?i)\d{1,2}[- /]?(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)[- /]?\d{2,4}", " ", text)
    text = re.sub(r"(?i)\d+:\d+(?::\d+)?(?:\s*(?:am|pm|ص|م))?", " ", text)
    text = re.sub(r"(?i)(?<![a-z])(?:egp|usd|eur|gbp|sar|aed|kwd|le)(?![a-z])", " ", text)
    text = re.sub(r"(?i)\bx+[\d*#-]+", " ", text)
    # Same as SmsPreview.shape: InstaPay addresses and masked digit runs such as 011XXXXX789 are values, not words.
    text = re.sub(r"(?i)[a-z0-9._-]+@instapay", " ", text)
    text = re.sub(r"(?i)(?<![^\W\d_])(?=[0-9x*#-]*[x*])(?=[0-9x*#-]*[0-9*#])[0-9x*#]+(?:-[0-9x*#]+)*(?![^\W\d_])", " ", text)
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
        assert all(row[col] == frozen_original[row["id"]][col] for col in ORIGINAL if (row["id"], col) not in OWNER_EXCEPTIONS)

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
