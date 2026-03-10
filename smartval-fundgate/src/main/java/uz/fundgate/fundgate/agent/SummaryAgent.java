package uz.fundgate.fundgate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uz.fundgate.fundgate.dto.AgentResult;
import uz.fundgate.fundgate.dto.SubmissionData;
import uz.fundgate.fundgate.dto.SummaryResult;
import uz.fundgate.fundgate.service.BedrockService;

import java.util.*;
import java.util.stream.Collectors;

import static uz.fundgate.fundgate.agent.CompletenessAgent.*;

/**
 * Summary Agent - Generates overall startup assessment.
 *
 * Produces trilingual:
 * - Strengths (4-6 points)
 * - Weaknesses (4-6 points)
 * - Overall comment (brief)
 * - Detailed comment (comprehensive, 6 paragraphs)
 * - Investment recommendation
 */
@Slf4j
@Component
public class SummaryAgent extends BaseAgent {

    private static final Map<String, Integer> MAX_SCORES = Map.of(
            "A", 20, "B", 20, "C", 20, "D", 15, "E", 15, "F", 10
    );
    private static final Map<String, String> CATEGORY_EN = Map.of(
            "A", "Data Completeness", "B", "Pitch Deck", "C", "Traction",
            "D", "Team", "E", "Product", "F", "Materials"
    );
    private static final Map<String, String> CATEGORY_RU = Map.of(
            "A", "Полнота данных", "B", "Pitch Deck", "C", "Тракшен",
            "D", "Команда", "E", "Продукт", "F", "Материалы"
    );
    private static final Map<String, String> CATEGORY_UZ = Map.of(
            "A", "Ma'lumotlar to'liqligi", "B", "Pitch Deck", "C", "Traction",
            "D", "Jamoa", "E", "Mahsulot", "F", "Materiallar"
    );

    public SummaryAgent(BedrockService bedrockService) {
        super("SummaryAgent", "Summary", 100, bedrockService);
    }

    @Override
    protected String getInstructions() {
        return """
                ## Summary Generation: Comprehensive Startup Investment Analysis

                You are an experienced venture capital analyst generating a professional investment memo.

                ### Output Requirements

                #### Strengths (4-6 detailed bullet points)
                Provide specific, evidence-based strengths with exact numbers.

                #### Weaknesses (4-6 detailed bullet points)
                Provide constructive, specific feedback explaining WHY it matters to investors.

                #### Overall Comment (Brief - 2-3 sentences)
                Concise executive summary.

                #### Detailed Comment (Comprehensive - 4-6 paragraphs)
                In-depth investment analysis covering Business Overview, Traction, Team, Product, Investment Readiness, and Verdict.

                #### Recommendation (Actionable - 2-3 sentences)
                Specific next steps based on status.

                ### Quality Standards
                1. Use specific numbers and data points
                2. Compare to industry benchmarks when possible
                3. Provide actionable recommendations
                4. Consider stage-appropriate expectations
                5. Write as if presenting to investment committee

                ### Language Requirements
                ALL outputs must be provided in THREE languages:
                - Russian (ru): Professional business Russian
                - English (en): Investment-quality English
                - Uzbek (uz): Latin script, professional business style""";
    }

    @Override
    protected String prepareInput(SubmissionData submission, Map<String, Object> extras) {
        // This is used for AI-based evaluation
        StringBuilder sb = new StringBuilder("## Startup Overview\n\n");
        sb.append("- **Name**: ").append(nvl(submission.getName())).append("\n");
        sb.append("- **Industry**: ").append(nvl(submission.getIndustry())).append("\n");
        sb.append("- **Stage**: ").append(nvl(submission.getStage())).append("\n");
        sb.append("- **Country**: ").append(nvl(submission.getCountry())).append("\n");
        return sb.toString();
    }

    @Override
    protected Map<String, Object> getToolSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    protected AgentResult parseToolResponse(JsonNode toolOutput) {
        return createErrorOutput("SummaryAgent uses generateHeuristic, not AI tool response");
    }

    @Override
    public AgentResult createErrorOutput(String errorMessage) {
        // SummaryAgent does not extend AgentResult; this returns a dummy
        return null;
    }

    /**
     * Create error SummaryResult.
     */
    public SummaryResult createErrorSummary(String errorMessage) {
        return SummaryResult.builder()
                .strengthsRu(List.of("Оценка не завершена"))
                .strengthsEn(List.of("Evaluation incomplete"))
                .strengthsUz(List.of("Baholash tugallanmagan"))
                .weaknessesRu(List.of("Не удалось проанализировать данные"))
                .weaknessesEn(List.of("Could not analyze data"))
                .weaknessesUz(List.of("Ma'lumotlarni tahlil qilib bo'lmadi"))
                .overallCommentRu("Ошибка генерации: " + errorMessage)
                .overallCommentEn("Generation error: " + errorMessage)
                .overallCommentUz("Yaratish xatosi: " + errorMessage)
                .recommendationRu("Повторите попытку")
                .recommendationEn("Please try again")
                .recommendationUz("Iltimos, qayta urinib ko'ring")
                .investmentReadiness("needs_improvement")
                .build();
    }

    /**
     * Generate detailed summary using heuristic rules (no AI call).
     * Translated from Python's generate_heuristic method.
     */
    public SummaryResult generateHeuristic(SubmissionData submission, Map<String, Integer> scores, int total) {
        String status;
        if (total >= 75) status = "ready_to_route";
        else if (total >= 50) status = "needs_improvement";
        else status = "blocked";

        String name = nvl(submission.getName()).isEmpty() ? "Unknown Startup" : submission.getName();
        String industry = nvl(submission.getIndustry()).isEmpty() ? "Technology" : submission.getIndustry();
        String stage = nvl(submission.getStage()).isEmpty() ? "Idea" : submission.getStage();
        double revenue = parseDouble(submission.getRevenue());
        int userCount = parseInt(submission.getUserCount());
        int teamSize = parseInt(submission.getTeamSize());
        if (teamSize < 1) teamSize = 1;
        String country = nvl(submission.getCountry()).isEmpty() ? "Unknown" : submission.getCountry();
        boolean hasIp = submission.isHasIP();

        // Calculate percentages
        Map<String, Double> percentages = new LinkedHashMap<>();
        for (String cat : List.of("A", "B", "C", "D", "E", "F")) {
            int score = scores.getOrDefault(cat, 0);
            int max = MAX_SCORES.get(cat);
            percentages.put(cat, max > 0 ? (score * 100.0 / max) : 0.0);
        }

        // Sort categories by percentage descending
        List<Map.Entry<String, Double>> sorted = percentages.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());

        // --- Strengths ---
        List<String> strengthsEn = new ArrayList<>(), strengthsRu = new ArrayList<>(), strengthsUz = new ArrayList<>();
        for (Map.Entry<String, Double> e : sorted) {
            if (e.getValue() >= 60 && strengthsEn.size() < 5) {
                addStrength(e.getKey(), e.getValue().intValue(), strengthsEn, strengthsRu, strengthsUz,
                        revenue, userCount, teamSize, stage, hasIp);
            }
        }
        if (strengthsEn.isEmpty()) {
            strengthsEn.add("Submission received and under evaluation - potential for improvement identified");
            strengthsRu.add("Заявка получена и оценивается - выявлен потенциал для улучшения");
            strengthsUz.add("Ariza qabul qilindi va baholanmoqda - yaxshilash imkoniyati aniqlandi");
        }

        // --- Weaknesses ---
        List<String> weaknessesEn = new ArrayList<>(), weaknessesRu = new ArrayList<>(), weaknessesUz = new ArrayList<>();
        List<Map.Entry<String, Double>> reverseSorted = new ArrayList<>(sorted);
        Collections.reverse(reverseSorted);
        for (Map.Entry<String, Double> e : reverseSorted) {
            if (e.getValue() < 60 && weaknessesEn.size() < 5) {
                addWeakness(e.getKey(), e.getValue().intValue(), weaknessesEn, weaknessesRu, weaknessesUz, revenue);
            }
        }

        // --- Overall comment ---
        String overallEn, overallRu, overallUz;
        if ("ready_to_route".equals(status)) {
            overallEn = String.format("%s is a %s-stage %s startup from %s that has achieved a strong score of %d/100 points. With demonstrated traction and comprehensive documentation, this startup is ready for investor matching.", name, stage, industry, country, total);
            overallRu = String.format("%s — это %s стартап на стадии %s из %s, набравший высокий балл %d/100. Благодаря подтвержденному тракшену и полной документации, стартап готов к подбору инвесторов.", name, industry, stage, country, total);
            overallUz = String.format("%s - %sdan %s bosqichidagi %s startapi bo'lib, %d/100 yuqori ball to'pladi. Tasdiqlangan traction va to'liq hujjatlar bilan startap investorlarni tanlashga tayyor.", name, country, stage, industry, total);
        } else if ("needs_improvement".equals(status)) {
            overallEn = String.format("%s is a %s-stage %s startup from %s scoring %d/100 points. While showing potential, several areas require strengthening before investor matching.", name, stage, industry, country, total);
            overallRu = String.format("%s — это %s стартап на стадии %s из %s, набравший %d/100 баллов. Несмотря на потенциал, некоторые области требуют укрепления перед подбором инвесторов.", name, industry, stage, country, total);
            overallUz = String.format("%s - %sdan %s bosqichidagi %s startapi bo'lib, %d/100 ball to'pladi. Potentsialni ko'rsatayotgan bo'lsa-da, investorlarni tanlashdan oldin bir nechta sohalarni mustahkamlash kerak.", name, country, stage, industry, total);
        } else {
            overallEn = String.format("%s is a %s-stage %s startup from %s with a score of %d/100 points. Critical improvements are needed in multiple areas before proceeding with evaluation.", name, stage, industry, country, total);
            overallRu = String.format("%s — это %s стартап на стадии %s из %s с оценкой %d/100 баллов. Необходимы критические улучшения в нескольких областях перед продолжением оценки.", name, industry, stage, country, total);
            overallUz = String.format("%s - %sdan %s bosqichidagi %s startapi bo'lib, %d/100 ball bilan baholandi. Baholashni davom ettirishdan oldin bir nechta sohalarda muhim yaxshilanishlar kerak.", name, country, stage, industry, total);
        }

        // --- Detailed comment ---
        String detailedEn = buildDetailedComment(name, industry, stage, country, revenue, userCount,
                teamSize, hasIp, total, status, percentages, sorted, "en");
        String detailedRu = buildDetailedComment(name, industry, stage, country, revenue, userCount,
                teamSize, hasIp, total, status, percentages, sorted, "ru");
        String detailedUz = buildDetailedComment(name, industry, stage, country, revenue, userCount,
                teamSize, hasIp, total, status, percentages, sorted, "uz");

        // --- Recommendation ---
        String recEn, recRu, recUz;
        if ("ready_to_route".equals(status)) {
            recEn = String.format("Proceed to fund matching. %s is well-positioned for seed-stage investment discussions with %s-focused VCs. Strong fundamentals and clear value proposition make this a compelling opportunity.", name, industry);
            recRu = String.format("Переходите к подбору фондов. %s хорошо позиционирован для обсуждений посевных инвестиций с VC, ориентированными на %s. Сильные фундаментальные показатели и четкое ценностное предложение делают это привлекательной возможностью.", name, industry);
            recUz = String.format("Fondlarni tanlashga o'ting. %s %s-ga yo'naltirilgan VC lar bilan urug' bosqichi investitsiya muhokamalari uchun yaxshi joylashgan. Kuchli asosiy ko'rsatkichlar va aniq qiymat taklifi buni jozibali imkoniyatga aylantiradi.", name, industry);
        } else if ("needs_improvement".equals(status)) {
            recEn = "Focus on these improvements: 1) Enhance pitch deck with required sections, 2) Add financial projections and metrics, 3) Strengthen team information. Target resubmission after addressing top 3 weakness areas.";
            recRu = "Сосредоточьтесь на этих улучшениях: 1) Улучшите pitch deck с необходимыми разделами, 2) Добавьте финансовые прогнозы и метрики, 3) Усильте информацию о команде. Планируйте повторную подачу после устранения 3 основных слабых мест.";
            recUz = "Ushbu yaxshilanishlarga e'tibor qarating: 1) Kerakli bo'limlar bilan pitch deck ni yaxshilang, 2) Moliyaviy prognozlar va ko'rsatkichlarni qo'shing, 3) Jamoa ma'lumotlarini kuchaytiring. Eng yuqori 3 ta zaif sohani bartaraf etgandan keyin qayta topshirishni maqsad qiling.";
        } else {
            recEn = "Critical blockers must be resolved: Complete all required fields, upload a valid pitch deck, and verify contact information. Address these fundamentals before further evaluation can proceed.";
            recRu = "Критические блокеры должны быть устранены: Заполните все обязательные поля, загрузите валидный pitch deck и проверьте контактную информацию. Устраните эти основы перед продолжением оценки.";
            recUz = "Muhim blokerlar hal qilinishi kerak: Barcha majburiy maydonlarni to'ldiring, to'g'ri pitch deck ni yuklang va kontakt ma'lumotlarini tekshiring. Baholashni davom ettirishdan oldin ushbu asoslarni hal qiling.";
        }

        return SummaryResult.builder()
                .strengthsRu(strengthsRu).strengthsEn(strengthsEn).strengthsUz(strengthsUz)
                .weaknessesRu(weaknessesRu).weaknessesEn(weaknessesEn).weaknessesUz(weaknessesUz)
                .overallCommentRu(overallRu).overallCommentEn(overallEn).overallCommentUz(overallUz)
                .detailedCommentRu(detailedRu).detailedCommentEn(detailedEn).detailedCommentUz(detailedUz)
                .recommendationRu(recRu).recommendationEn(recEn).recommendationUz(recUz)
                .investmentReadiness(status)
                .build();
    }

    // --- Private helpers ---

    private void addStrength(String cat, int pct,
                             List<String> en, List<String> ru, List<String> uz,
                             double revenue, int userCount, int teamSize, String stage, boolean hasIp) {
        switch (cat) {
            case "A" -> {
                en.add(String.format("Complete submission with %d%% data coverage - all required fields properly filled", pct));
                ru.add(String.format("Полная заявка с %d%% заполненностью данных - все обязательные поля корректно заполнены", pct));
                uz.add(String.format("To'liq ariza %d%% ma'lumot qamrovi bilan - barcha majburiy maydonlar to'g'ri to'ldirilgan", pct));
            }
            case "B" -> {
                en.add(String.format("Strong pitch deck presentation scoring %d%% - clear value proposition and structure", pct));
                ru.add(String.format("Сильная pitch deck презентация с оценкой %d%% - четкое ценностное предложение и структура", pct));
                uz.add(String.format("Kuchli pitch deck taqdimoti %d%% ball bilan - aniq qiymat taklifi va tuzilish", pct));
            }
            case "C" -> {
                if (revenue > 0) {
                    en.add(String.format("Demonstrated traction with $%,.0f revenue and %,d users", revenue, userCount));
                    ru.add(String.format("Подтвержденный тракшен: выручка $%,.0f и %,d пользователей", revenue, userCount));
                    uz.add(String.format("Tasdiqlangan traction: $%,.0f daromad va %,d foydalanuvchi", revenue, userCount));
                } else {
                    en.add(String.format("Growing user base of %,d users indicates product interest", userCount));
                    ru.add(String.format("Растущая база из %,d пользователей указывает на интерес к продукту", userCount));
                    uz.add(String.format("%,d foydalanuvchidan iborat o'suvchi baza mahsulotga qiziqishni ko'rsatadi", userCount));
                }
            }
            case "D" -> {
                en.add(String.format("Team of %d members showing solid organizational foundation", teamSize));
                ru.add(String.format("Команда из %d человек демонстрирует прочную организационную основу", teamSize));
                uz.add(String.format("%d a'zodan iborat jamoa mustahkam tashkiliy asosni ko'rsatadi", teamSize));
            }
            case "E" -> {
                if (hasIp) {
                    en.add(String.format("Product at %s stage with intellectual property protection", stage));
                    ru.add(String.format("Продукт на стадии %s с защитой интеллектуальной собственности", stage));
                    uz.add(String.format("Intellektual mulk himoyasi bilan %s bosqichidagi mahsulot", stage));
                } else {
                    en.add(String.format("Product development at %s stage demonstrates execution capability", stage));
                    ru.add(String.format("Разработка продукта на стадии %s демонстрирует способность к реализации", stage));
                    uz.add(String.format("%s bosqichidagi mahsulot ishlab chiqish bajarish qobiliyatini ko'rsatadi", stage));
                }
            }
            case "F" -> {
                en.add(String.format("Well-prepared supporting materials with %d%% quality score", pct));
                ru.add(String.format("Хорошо подготовленные сопроводительные материалы с оценкой качества %d%%", pct));
                uz.add(String.format("Yaxshi tayyorlangan qo'llab-quvvatlash materiallari %d%% sifat bahosi bilan", pct));
            }
        }
    }

    private void addWeakness(String cat, int pct,
                             List<String> en, List<String> ru, List<String> uz,
                             double revenue) {
        switch (cat) {
            case "A" -> {
                en.add(String.format("Data completeness at %d%% - missing required fields reduce investor confidence", pct));
                ru.add(String.format("Полнота данных %d%% - отсутствие обязательных полей снижает доверие инвесторов", pct));
                uz.add(String.format("Ma'lumotlar to'liqligi %d%% - majburiy maydonlar yo'qligi investor ishonchini kamaytiradi", pct));
            }
            case "B" -> {
                en.add(String.format("Pitch deck scored %d%% - consider adding Problem, Solution, Market, and Ask slides", pct));
                ru.add(String.format("Pitch deck оценен на %d%% - рекомендуется добавить слайды Problem, Solution, Market и Ask", pct));
                uz.add(String.format("Pitch deck %d%% baho oldi - Problem, Solution, Market va Ask slaydlarini qo'shishni ko'rib chiqing", pct));
            }
            case "C" -> {
                if (revenue == 0) {
                    en.add("No revenue yet - pre-revenue startups face higher fundraising challenges");
                    ru.add("Пока нет выручки - стартапы без выручки сталкиваются с большими трудностями при привлечении средств");
                    uz.add("Hali daromad yo'q - daromadsiz startaplar ko'proq mablag' yig'ish qiyinchiliklariga duch keladi");
                } else {
                    en.add(String.format("Traction metrics at %d%% - demonstrate stronger growth trajectory", pct));
                    ru.add(String.format("Метрики тракшена на %d%% - необходимо продемонстрировать более сильную траекторию роста", pct));
                    uz.add(String.format("Traction ko'rsatkichlari %d%% - kuchli o'sish traektoriyasini ko'rsating", pct));
                }
            }
            case "D" -> {
                en.add(String.format("Team assessment at %d%% - consider adding co-founders or key advisors", pct));
                ru.add(String.format("Оценка команды %d%% - рекомендуется привлечь сооснователей или ключевых консультантов", pct));
                uz.add(String.format("Jamoa bahosi %d%% - hammuassislar yoki asosiy maslahatchilarni jalb qilishni ko'rib chiqing", pct));
            }
            case "E" -> {
                en.add(String.format("Product evaluation at %d%% - strengthen IP protection or technical moat", pct));
                ru.add(String.format("Оценка продукта %d%% - усильте защиту IP или технологический барьер", pct));
                uz.add(String.format("Mahsulot bahosi %d%% - IP himoyasi yoki texnik moat ni kuchaytiring", pct));
            }
            case "F" -> {
                en.add(String.format("Materials quality at %d%% - add one-pager and financial projections", pct));
                ru.add(String.format("Качество материалов %d%% - добавьте one-pager и финансовые прогнозы", pct));
                uz.add(String.format("Materiallar sifati %d%% - one-pager va moliyaviy prognozlarni qo'shing", pct));
            }
        }
    }

    private String buildDetailedComment(String name, String industry, String stage, String country,
                                        double revenue, int userCount, int teamSize, boolean hasIp,
                                        int total, String status,
                                        Map<String, Double> percentages,
                                        List<Map.Entry<String, Double>> sorted,
                                        String lang) {
        StringBuilder sb = new StringBuilder();

        // P1: Business Overview
        switch (lang) {
            case "en" -> sb.append(String.format("**Business Overview:** %s operates in the %s sector at the %s development stage. Based in %s, the company targets the local and regional market with its offering.", name, industry, stage, country));
            case "ru" -> sb.append(String.format("**Обзор бизнеса:** %s работает в секторе %s на стадии развития %s. Компания базируется в %s и нацелена на местный и региональный рынок.", name, industry, stage, country));
            case "uz" -> sb.append(String.format("**Biznes sharhi:** %s %s sektorida %s rivojlanish bosqichida faoliyat yuritadi. %sda joylashgan kompaniya mahalliy va mintaqaviy bozorni maqsad qiladi.", name, industry, stage, country));
        }
        sb.append("\n\n");

        // P2: Traction
        double pctC = percentages.getOrDefault("C", 0.0);
        String tractionLevel;
        if (revenue > 0) {
            switch (lang) {
                case "en" -> {
                    tractionLevel = pctC >= 70 ? "strong" : pctC >= 50 ? "moderate" : "developing";
                    sb.append(String.format("**Traction Analysis:** The startup has achieved $%,.0f in revenue with %,d users. This indicates early market validation and customer willingness to pay. Growth metrics suggest %s momentum.", revenue, userCount, tractionLevel));
                }
                case "ru" -> {
                    tractionLevel = pctC >= 70 ? "сильном" : pctC >= 50 ? "умеренном" : "развивающемся";
                    sb.append(String.format("**Анализ тракшена:** Стартап достиг выручки $%,.0f с %,d пользователями. Это указывает на раннюю валидацию рынка и готовность клиентов платить. Метрики роста свидетельствуют о %s импульсе.", revenue, userCount, tractionLevel));
                }
                case "uz" -> {
                    tractionLevel = pctC >= 70 ? "kuchli" : pctC >= 50 ? "o'rtacha" : "rivojlanayotgan";
                    sb.append(String.format("**Traction tahlili:** Startap %,d foydalanuvchi bilan $%,.0f daromadga erishdi. Bu erta bozor validatsiyasi va mijozlarning to'lashga tayyorligini ko'rsatadi. O'sish ko'rsatkichlari %s momentni ko'rsatadi.", userCount, revenue, tractionLevel));
                }
            }
        } else {
            switch (lang) {
                case "en" -> sb.append(String.format("**Traction Analysis:** Currently pre-revenue with %,d users. While building user base is positive, revenue generation should be prioritized to demonstrate business model viability.", userCount));
                case "ru" -> sb.append(String.format("**Анализ тракшена:** В настоящее время без выручки с %,d пользователями. Хотя формирование базы пользователей положительно, генерация выручки должна быть приоритетной для демонстрации жизнеспособности бизнес-модели.", userCount));
                case "uz" -> sb.append(String.format("**Traction tahlili:** Hozirda %,d foydalanuvchi bilan daromadsiz. Foydalanuvchi bazasini shakllantirish ijobiy bo'lsa-da, biznes modeli hayotiyligini ko'rsatish uchun daromad yaratish ustuvor bo'lishi kerak.", userCount));
            }
        }
        sb.append("\n\n");

        // P3: Team
        switch (lang) {
            case "en" -> sb.append(String.format("**Team Assessment:** The founding team consists of %d %s. %s Key roles and relevant industry experience should be highlighted.", teamSize, teamSize == 1 ? "member" : "members", teamSize == 1 ? "Solo founder setup increases execution risk - consider adding co-founders." : "Team size provides basic operational capability."));
            case "ru" -> sb.append(String.format("**Оценка команды:** Команда основателей состоит из %d %s. %s Следует выделить ключевые роли и соответствующий отраслевой опыт.", teamSize, teamSize == 1 ? "человека" : "человек", teamSize == 1 ? "Единоличный основатель повышает риск исполнения - рекомендуется привлечь сооснователей." : "Размер команды обеспечивает базовые операционные возможности."));
            case "uz" -> sb.append(String.format("**Jamoa bahosi:** Asoschilar jamoasi %d a'zodan iborat. %s Asosiy rollar va tegishli sanoat tajribasini ta'kidlash kerak.", teamSize, teamSize == 1 ? "Yagona asoschining o'rnatilishi bajarish xavfini oshiradi - hammuassislarni jalb qilishni ko'rib chiqing." : "Jamoa hajmi asosiy operatsion imkoniyatni ta'minlaydi."));
        }
        sb.append("\n\n");

        // P4: Product
        switch (lang) {
            case "en" -> sb.append(String.format("**Product & Technology:** The product is at %s stage %s. %s Technical maturity aligns with the stated development stage.", stage, hasIp ? "with intellectual property protection" : "without formal IP protection", hasIp ? "Strong defensibility through IP creates competitive moat." : "Consider developing proprietary technology or securing IP to strengthen defensibility."));
            case "ru" -> sb.append(String.format("**Продукт и технология:** Продукт находится на стадии %s %s. %s Техническая зрелость соответствует заявленной стадии разработки.", stage, hasIp ? "с защитой интеллектуальной собственности" : "без формальной защиты IP", hasIp ? "Сильная защищаемость через IP создает конкурентный барьер." : "Рекомендуется разработать проприетарную технологию или защитить IP для укрепления позиций."));
            case "uz" -> sb.append(String.format("**Mahsulot va texnologiya:** Mahsulot %s bosqichida %s. %s Texnik yetuklik e'lon qilingan rivojlanish bosqichiga mos keladi.", stage, hasIp ? "intellektual mulk himoyasi bilan" : "rasmiy IP himoyasisiz", hasIp ? "IP orqali kuchli himoyalanish raqobat to'sig'ini yaratadi." : "Himoyani kuchaytirish uchun xususiy texnologiya ishlab chiqish yoki IP ni himoya qilishni ko'rib chiqing."));
        }
        sb.append("\n\n");

        // P5: Investment Readiness
        switch (lang) {
            case "en" -> {
                if ("ready_to_route".equals(status)) sb.append(String.format("**Investment Readiness:** With a score of %d/100, this startup demonstrates strong readiness for investor matching. All key criteria are met. Minor optimizations could further strengthen the application.", total));
                else if ("needs_improvement".equals(status)) sb.append(String.format("**Investment Readiness:** Scoring %d/100 indicates potential but requires improvement. Addressing these gaps could increase score by 10-20 points.", total));
                else sb.append(String.format("**Investment Readiness:** A score of %d/100 indicates significant gaps that must be addressed. Critical blockers prevent investor matching at this stage.", total));
            }
            case "ru" -> {
                if ("ready_to_route".equals(status)) sb.append(String.format("**Готовность к инвестициям:** С оценкой %d/100, этот стартап демонстрирует высокую готовность к подбору инвесторов. Все ключевые критерии выполнены.", total));
                else if ("needs_improvement".equals(status)) sb.append(String.format("**Готовность к инвестициям:** Оценка %d/100 указывает на потенциал, но требует улучшений. Устранение этих пробелов может увеличить балл на 10-20 пунктов.", total));
                else sb.append(String.format("**Готовность к инвестициям:** Оценка %d/100 указывает на значительные пробелы, которые необходимо устранить. Критические блокеры препятствуют подбору инвесторов.", total));
            }
            case "uz" -> {
                if ("ready_to_route".equals(status)) sb.append(String.format("**Investitsiyaga tayyorlik:** %d/100 ball bilan bu startap investorlarni tanlashga kuchli tayyorlikni ko'rsatadi. Barcha asosiy mezonlar bajarilgan.", total));
                else if ("needs_improvement".equals(status)) sb.append(String.format("**Investitsiyaga tayyorlik:** %d/100 ball potentsialni ko'rsatadi, lekin yaxshilash talab etiladi. Bu bo'shliqlarni bartaraf etish ballni 10-20 ballga oshirishi mumkin.", total));
                else sb.append(String.format("**Investitsiyaga tayyorlik:** %d/100 ball hal qilinishi kerak bo'lgan muhim bo'shliqlarni ko'rsatadi. Muhim blokerlar ushbu bosqichda investorlarni tanlashga to'sqinlik qiladi.", total));
            }
        }
        sb.append("\n\n");

        // P6: Verdict
        switch (lang) {
            case "en" -> {
                if ("ready_to_route".equals(status)) sb.append(String.format("**Verdict:** %s is ready for fund matching. Recommended next step: proceed to investor matching with focus on %s-focused seed-stage funds.", name, industry));
                else if ("needs_improvement".equals(status)) sb.append(String.format("**Verdict:** %s shows promise but needs refinement. Priority actions: strengthen pitch deck, add financial projections, and demonstrate clearer traction.", name));
                else sb.append(String.format("**Verdict:** %s requires substantial improvements before proceeding. Complete all required fields, upload pitch deck, and ensure valid contact information.", name));
            }
            case "ru" -> {
                if ("ready_to_route".equals(status)) sb.append(String.format("**Вердикт:** %s готов к подбору фондов. Рекомендуемый следующий шаг: переход к подбору инвесторов с фокусом на посевные фонды в сфере %s.", name, industry));
                else if ("needs_improvement".equals(status)) sb.append(String.format("**Вердикт:** %s показывает потенциал, но требует доработки. Приоритетные действия: усилить pitch deck, добавить финансовые прогнозы и продемонстрировать более четкий тракшен.", name));
                else sb.append(String.format("**Вердикт:** %s требует существенных улучшений перед продолжением. Заполните все обязательные поля, загрузите pitch deck и убедитесь в корректности контактной информации.", name));
            }
            case "uz" -> {
                if ("ready_to_route".equals(status)) sb.append(String.format("**Xulosa:** %s fondlarni tanlashga tayyor. Tavsiya etilgan keyingi qadam: %s-ga yo'naltirilgan urug' bosqichi fondlariga investorlarni tanlashga o'tish.", name, industry));
                else if ("needs_improvement".equals(status)) sb.append(String.format("**Xulosa:** %s istiqbolni ko'rsatadi, lekin takomillashtirishni talab qiladi. Ustuvor harakatlar: pitch deck ni mustahkamlash, moliyaviy prognozlarni qo'shish va aniqroq tractionni ko'rsatish.", name));
                else sb.append(String.format("**Xulosa:** %s davom ettirishdan oldin jiddiy yaxshilanishlarni talab qiladi. Barcha majburiy maydonlarni to'ldiring, pitch deck ni yuklang va kontakt ma'lumotlari to'g'riligiga ishonch hosil qiling.", name));
            }
        }

        return sb.toString();
    }
}
