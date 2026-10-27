package com.mohamedgamal.masareef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Why the new pipeline asks the user to review a transaction, in the user's language. Pure. */
final class ReviewReasons {
    /** Review reasons in the order they are explained (most specific first). */
    static final String[][] TEXT = {
        {"ATM_TERMINAL", "الاسم كود والمبلغ ممكن يكون سحب نقدي؛ ده اقتراح محتاج تأكيدك. لو شراء، غيّر التصنيف"},
        {"OFFSET_UNLINKED", "مبلغ رجع لحسابك (استرداد أو إلغاء) ومش لاقيين المصروف الأصلي؛ اربطه بيه"},
        {"TRANSFER_REQUESTED", "طلب تحويل، والبنك ما أكدش إنه اتنفذ"},
        {"CURRENCY_INFERRED", "الرسالة ما ذكرتش العملة؛ اعتبرناها جنيه مصري"},
        {"PAYER_UNKNOWN", "تأكيد من مزود الخدمة من غير ما يذكر اتدفع منين؛ ممكن تكون متسجلة من رسالة البنك أو المحفظة"},
        {"POSSIBLE_INTERNAL_TRANSFER", "ممكن تكون تحويل بين حساباتك، مش مصروف ولا دخل"},
        {"EXPIRING_CREDIT", "رصيد مضاف ليه تاريخ انتهاء"},
        {"UNASSIGNED_SECOND_AMOUNT", "في الرسالة مبلغ تاني مش واضح دوره"},
        {"COMMITMENT_EVENT", "طلب شراء أو التزام، لسه مش خصم فعلي"},
        {"RETAINED_FEE", "عملية ما تمتش واتخصمت رسومها"},
        {"ADJUSTED_EVENT", "عملية اتعدلت بعد تسجيلها"},
        {"MISSING_MERCHANT", "اسم التاجر مش واضح في الرسالة"},
        {"INFERRED_OUTGOING_DIRECTION", "نوعها مستنتج (غالبًا مصروف)؛ الرسالة ما قالتش خصم ولا إضافة صراحة"},
        {"LOW_CONFIDENCE", "الرسالة مش واضحة كفاية"},
    };

    /** The sentences for these reasons, most specific first; a generic sentence when none is known. */
    static List<String> text(Collection<String> reasons) {
        List<String> out = new ArrayList<>();
        for (String[] entry : TEXT) if (reasons.contains(entry[0])) out.add(entry[1]);
        if (out.isEmpty()) out.add("محتاجة تتأكد منها");
        return out;
    }

    private ReviewReasons() {}
}
