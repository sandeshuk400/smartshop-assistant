package com.smartshop.assistant;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final String SUPPORT_EMAIL = "support@example.com";
    private static final String SUPPORT_PHONE = "+91-0000000000";
    private static final String SUPPORT_HOURS = "Monday to Saturday 9 AM to 6 PM, Sunday 9 AM to 3 PM";

    private static final String CONTACT = "our support team at " + SUPPORT_EMAIL
            + " or call " + SUPPORT_PHONE + " (" + SUPPORT_HOURS + ")";

    private static final String FALLBACK = "Sorry, I don't have information about that. "
            + "Please call " + SUPPORT_PHONE + " or email " + SUPPORT_EMAIL + ".";

    private static final Pattern ORDER_NO = Pattern.compile(
            "(?i)\\border\\s*(?:number|no\\.?|id|#)?\\s*#?\\s*(\\d{1,18})");
    private static final Pattern CUSTOMER_NO = Pattern.compile(
            "(?i)\\bcustomer\\s*(?:id|number|no\\.?)?\\s*(?:is\\s*)?#?\\s*(\\d{1,18})");
    // The 6-digit code printed on the bill, for example "code 123456".
    private static final Pattern BILL_CODE = Pattern.compile(
            "(?i)\\b(?:bill\\s*)?code\\s*(?:is\\s*)?#?\\s*(\\d{6})\\b");
    // A message that is only a number, such as "1" or "#1".
    private static final Pattern BARE_NUMBER = Pattern.compile("^\\s*#?(\\d{1,18})\\s*$");

    // ---- Return rules (checked in code, so the AI model cannot get them wrong) ----
    private static final BigDecimal MIN_RETURN_PRICE = new BigDecimal("100");

    private static final Pattern CAN_I_WORDS = Pattern.compile(
            "(?i)\\b(can|could|may|allowed|possible|eligible)\\b");

    private static final Pattern DAMAGE_WORDS = Pattern.compile(
            "(?i)\\b(damaged?|spoiled|spoilt|expired|broken|torn|leaking|leaked|defective|"
                    + "wrong\\s+(?:item|product|order|brand|packet|one|size))\\b");

    private static final Pattern POLICY_WORDS = Pattern.compile(
            "(?i)\\b(policy|policies|rules?|terms|conditions|how\\s+(?:does|do)\\s+returns?\\s+work)\\b");

    private static final Pattern DAYS_WORDS = Pattern.compile(
            "(?i)\\b(how\\s+many\\s+days|how\\s+long|how\\s+much\\s+time|days|deadline|time\\s+limit|"
                    + "window|last\\s+date|by\\s+when|until\\s+when|within)\\b");

    private static final Pattern RETURN_WORDS = Pattern.compile(
            "(?i)\\b(return|returns|returned|returning|send\\s+back|sending\\s+back|give\\s+back)\\b");

    // Edit this list to match what your shop sells. Whole words only.
    private static final Pattern EDIBLE_WORDS = Pattern.compile(
            "(?i)\\b(biscuits?|snacks?|chips|namkeen|rice|flour|atta|maida|oil|spices?|masala|"
                    + "tea|coffee|sweets?|sugar|salt|dal|pulses|milk|food|foods|edible|"
                    + "chocolates?|noodles|bread|juice)\\b");

    // Matches "Rs 50", "Rs.50", "50 rupees", "rupees 50".
    private static final Pattern PRICE = Pattern.compile(
            "(?i)(?:rs\\.?|\u20B9|rupees?)\\s*(\\d+(?:\\.\\d+)?)|(\\d+(?:\\.\\d+)?)\\s*(?:rs\\.?|rupees?)");

    // ---- Shipping, payment and refund rules (fixed replies, also checked in code) ----
    private static final Pattern REFUND_WORDS = Pattern.compile(
            "(?i)\\b(refunds?|refunded|money\\s+back)\\b");
    private static final Pattern COD_WORDS = Pattern.compile(
            "(?i)(cash\\s+on\\s+delivery|\\bcod\\b)");
    private static final Pattern PAYMENT_WORDS = Pattern.compile(
            "(?i)\\b(payments?|pay|paying|upi|net\\s*banking|credit\\s+card|debit\\s+card)\\b");
    private static final Pattern SHIP_WORDS = Pattern.compile(
            "(?i)\\b(ship|shipping|shipped|deliver|delivery|delivered|courier|dispatch)\\b");
    private static final Pattern ABROAD_WORDS = Pattern.compile(
            "(?i)\\b(outside\\s+india|abroad|international|overseas|foreign|other\\s+countr(?:y|ies))\\b");
    private static final Pattern AREA_WORDS = Pattern.compile(
            "(?i)\\b(nagpur|my\\s+(?:area|city|town|location)|pin\\s*code|rural|urban|areas?|cities|city|locations?)\\b");
    private static final Pattern COST_WORDS = Pattern.compile(
            "(?i)\\b(cost|costs|charges?|fees?|free|price|how\\s+much|rupees|rs)\\b");
    private static final Pattern TIME_WORDS = Pattern.compile(
            "(?i)\\b(how\\s+long|how\\s+many\\s+days|days|time|when|fast|quick|express|standard|take|takes|arrive|reach)\\b");

    // Short-term memory of the order conversation, kept per browser session.
    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final int MAX_SESSIONS = 5000;

    // ---- Guess protection: too many failed order lookups from one IP pauses lookups ----
    private static final int MAX_FAILED_LOOKUPS = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_IPS = 10000;

    private record Pending(Long orderId, Long customerId, String code, Instant at) {
    }

    private record Failures(int count, Instant lastAt) {
    }

    private final Map<String, Pending> sessions = new ConcurrentHashMap<>();
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final OrderRepository orders;

    public ChatController(ChatClient.Builder builder, VectorStore vectorStore, OrderRepository orders) {
        this.vectorStore = vectorStore;
        this.orders = orders;
        this.chatClient = builder
                .defaultSystem("""
                        You are the customer support assistant for Avantika Provisions.
                        Be friendly, short and clear.
                        Use only the provided context. Never guess or use outside knowledge.
                        """)
                .build();
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest http) {
        requireMessage(request.message());

        Optional<String> returnReply = returnRuleReply(request.message());
        if (returnReply.isPresent()) {
            return new ChatResponse(returnReply.get());
        }

        Optional<String> orderReply = orderReply(request.message(), request.sessionId(), http.getRemoteAddr());
        if (orderReply.isPresent()) {
            return new ChatResponse(orderReply.get());
        }

        Optional<String> shopReply = shippingPaymentReply(request.message());
        if (shopReply.isPresent()) {
            return new ChatResponse(shopReply.get());
        }

        String context = findContext(request.message());
        if (context == null) {
            return new ChatResponse(FALLBACK);
        }

        String reply = chatClient.prompt()
                .user(buildPrompt(context, request.message()))
                .call()
                .content();

        return new ChatResponse(reply);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String message,
                               @RequestParam(required = false) String sessionId,
                               HttpServletRequest http) {
        requireMessage(message);

        Optional<String> returnReply = returnRuleReply(message);
        if (returnReply.isPresent()) {
            return Flux.just(returnReply.get());
        }

        Optional<String> orderReply = orderReply(message, sessionId, http.getRemoteAddr());
        if (orderReply.isPresent()) {
            return Flux.just(orderReply.get());
        }

        Optional<String> shopReply = shippingPaymentReply(message);
        if (shopReply.isPresent()) {
            return Flux.just(shopReply.get());
        }

        String context = findContext(message);
        if (context == null) {
            return Flux.just(FALLBACK);
        }

        return chatClient.prompt()
                .user(buildPrompt(context, message))
                .stream()
                .content();
    }

    /**
     * Fixed answers for refunds, shipping and payment questions.
     * Empty means: no rule applies, so let the FAQ answer.
     */
    private Optional<String> shippingPaymentReply(String message) {
        boolean ship = SHIP_WORDS.matcher(message).find();
        boolean cost = COST_WORDS.matcher(message).find();
        boolean time = TIME_WORDS.matcher(message).find();

        if (REFUND_WORDS.matcher(message).find()) {
            return Optional.of("Your refund is processed within 1 day. "
                    + "For cash on delivery orders, the refund takes 5 to 7 working days.");
        }

        if (ship && ABROAD_WORDS.matcher(message).find()) {
            return Optional.of("Yes, we ship outside India. The charges vary depending on how far the "
                    + "delivery location is from our shop. Please call " + SUPPORT_PHONE + " or email "
                    + SUPPORT_EMAIL + " to get the exact charge for your location.");
        }

        if (COD_WORDS.matcher(message).find()) {
            return Optional.of("Yes, we accept cash on delivery for orders up to Rs 10000. "
                    + "There is no extra fee for cash on delivery.");
        }

        if (PAYMENT_WORDS.matcher(message).find()) {
            return Optional.of("We accept online payment, UPI, and cash on delivery. "
                    + "Cash on delivery is available for orders up to Rs 10000, with no extra fee.");
        }

        if (ship && AREA_WORDS.matcher(message).find() && !cost && !time) {
            return Optional.of("We deliver all over Nagpur, in both urban and rural areas. "
                    + "For other locations, please call " + SUPPORT_PHONE + " or email " + SUPPORT_EMAIL + ".");
        }

        if (ship && cost) {
            return Optional.of("Standard shipping is free on orders above Rs 500. "
                    + "For orders below Rs 500, the delivery charge is up to Rs 50. "
                    + "Express shipping costs Rs 200.");
        }

        if (ship && time) {
            return Optional.of("Standard shipping takes 2 to 5 working days. "
                    + "Express shipping delivers within 1 day.");
        }

        return Optional.empty();
    }

    /**
     * Answers return questions: damaged items, the full policy, edible items,
     * items under Rs 100, and "Can I return X?".
     * Empty means: no rule applies, so let the normal flow answer.
     */
    private Optional<String> returnRuleReply(String message) {
        // Damaged, spoiled, expired or wrong items follow the shop's own policy.
        if (DAMAGE_WORDS.matcher(message).find()) {
            return Optional.of("If you receive a damaged, spoiled, expired, or wrong item, please call or "
                    + "WhatsApp " + SUPPORT_PHONE + " within 24 hours of delivery and send a photo of the item. "
                    + "We will send a replacement or refund you. This applies to food items too.");
        }

        if (!RETURN_WORDS.matcher(message).find()) {
            return Optional.empty();
        }

        // "What is your return policy?" gets the full fixed policy.
        if (POLICY_WORDS.matcher(message).find()) {
            return Optional.of("Here is our return policy:\n"
                    + "- Return window: 3 working days after delivery (Monday to Saturday, Sundays not counted).\n"
                    + "- To return an item: open your order and tap the Return of Item button.\n"
                    + "- Cannot be returned: edible products and items priced below Rs 100.\n"
                    + "- Damaged, spoiled, expired, or wrong item: call or WhatsApp " + SUPPORT_PHONE
                    + " within 24 hours of delivery and send a photo. We will send a replacement "
                    + "or refund you. This applies to food items too.\n"
                    + "- Refunds: processed within 1 day. Cash on delivery refunds take 5 to 7 working days.");
        }

        if (EDIBLE_WORDS.matcher(message).find()) {
            return Optional.of("Sorry, edible products such as food items cannot be returned. "
                    + "If the item arrived damaged, spoiled, expired, or wrong, call or WhatsApp "
                    + SUPPORT_PHONE + " within 24 hours of delivery with a photo.");
        }

        Matcher price = PRICE.matcher(message);
        while (price.find()) {
            String number = price.group(1) != null ? price.group(1) : price.group(2);
            if (new BigDecimal(number).compareTo(MIN_RETURN_PRICE) < 0) {
                return Optional.of("Sorry, items priced below Rs 100 cannot be returned. "
                        + "If the item arrived damaged, spoiled, expired, or wrong, call or WhatsApp "
                        + SUPPORT_PHONE + " within 24 hours of delivery with a photo.");
            }
        }

        // "How many days do I have to return?" gets the full window, with the Sunday rule.
        if (DAYS_WORDS.matcher(message).find()) {
            return Optional.of("You can return an eligible item within 3 working days of delivery. "
                    + "Working days are Monday to Saturday, and Sundays are not counted. "
                    + "Edible products and items priced below Rs 100 cannot be returned. "
                    + "If an item arrived damaged, spoiled, expired, or wrong, call or WhatsApp "
                    + SUPPORT_PHONE + " within 24 hours of delivery with a photo.");
        }

        // "Can I return X?" with no rule hit: give the fixed conditions, not an AI guess.
        if (CAN_I_WORDS.matcher(message).find()) {
            return Optional.of("Items can be returned within 3 working days of delivery (Sundays are not counted), "
                    + "unless they are edible products or priced below Rs 100. "
                    + "If your item is neither, open your order and tap the Return of Item button. "
                    + "If an item arrived damaged, spoiled, expired, or wrong, call or WhatsApp "
                    + SUPPORT_PHONE + " within 24 hours of delivery with a photo.");
        }

        return Optional.empty();
    }

    /**
     * Handles order questions, including ones split over several messages.
     * The customer must give the order number, customer ID and the 6-digit bill code.
     * Empty means: not an order question, so use the next check.
     */
    private Optional<String> orderReply(String message, String sessionId, String ip) {
        Pending prev = recall(sessionId);

        Long orderId = null;
        Long customerId = null;
        String code = null;

        Matcher orderMatch = ORDER_NO.matcher(message);
        if (orderMatch.find()) {
            orderId = Long.parseLong(orderMatch.group(1));
        }
        Matcher customerMatch = CUSTOMER_NO.matcher(message);
        if (customerMatch.find()) {
            customerId = Long.parseLong(customerMatch.group(1));
        }
        Matcher codeMatch = BILL_CODE.matcher(message);
        if (codeMatch.find()) {
            code = codeMatch.group(1);
        }

        // We just asked for the customer ID, and the reply is only a number.
        if (orderId == null && customerId == null && code == null
                && prev.orderId() != null && prev.customerId() == null) {
            Matcher bare = BARE_NUMBER.matcher(message);
            if (bare.matches()) {
                customerId = Long.parseLong(bare.group(1));
            }
        }

        if (orderId == null && customerId == null && code == null) {
            return Optional.empty();
        }

        // Fill the gaps from what was said earlier in this session.
        if (orderId == null) {
            orderId = prev.orderId();
        }
        if (customerId == null) {
            customerId = prev.customerId();
        }
        if (code == null) {
            code = prev.code();
        }

        if (orderId == null) {
            remember(sessionId, null, customerId, code);
            return Optional.of("Thanks. Which order number should I check? "
                    + "For example: \"Where is my order 123?\"");
        }
        if (customerId == null) {
            remember(sessionId, orderId, null, code);
            return Optional.of("To check order " + orderId + ", please also tell me your customer ID. "
                    + "You can just reply with the number.");
        }
        if (code == null) {
            remember(sessionId, orderId, customerId, null);
            return Optional.of("For your security, please also send the 6-digit code printed on your bill. "
                    + "For example: \"code 123456\".");
        }

        // Too many wrong guesses from this address: pause lookups, do not touch the database.
        if (isLockedOut(ip)) {
            return Optional.of("Too many unsuccessful attempts. For your security, order lookups are "
                    + "paused for 15 minutes. Please try again later, or contact " + CONTACT + ".");
        }

        Optional<OrderRepository.Order> order = orders.findByIdCustomerAndCode(orderId, customerId, code);
        if (order.isPresent()) {
            // Keep the customer ID so they can ask about another order, but not the code.
            remember(sessionId, null, customerId, null);
            return Optional.of(describe(order.get()));
        }

        // Keep only the order number. The customer ID and code must be given again.
        recordFailure(ip);
        remember(sessionId, orderId, null, null);
        return Optional.of("I couldn't find an order matching those details. "
                + "Please check the order number, customer ID and bill code, or contact " + CONTACT + ".");
    }

    private boolean isLockedOut(String ip) {
        Failures f = failures.get(ip);
        if (f == null) {
            return false;
        }
        if (f.lastAt().isBefore(Instant.now().minus(LOCKOUT))) {
            failures.remove(ip);
            return false;
        }
        return f.count() >= MAX_FAILED_LOOKUPS;
    }

    private void recordFailure(String ip) {
        if (failures.size() >= MAX_TRACKED_IPS) {
            Instant cutoff = Instant.now().minus(LOCKOUT);
            failures.values().removeIf(f -> f.lastAt().isBefore(cutoff));
            if (failures.size() >= MAX_TRACKED_IPS) {
                return;
            }
        }
        Instant now = Instant.now();
        Instant cutoff = now.minus(LOCKOUT);
        failures.merge(ip, new Failures(1, now), (old, fresh) ->
                old.lastAt().isBefore(cutoff) ? fresh : new Failures(old.count() + 1, now));
    }

    private Pending recall(String sessionId) {
        Pending empty = new Pending(null, null, null, Instant.now());
        if (sessionId == null || sessionId.isBlank()) {
            return empty;
        }
        Pending found = sessions.get(sessionId);
        if (found == null || found.at().isBefore(Instant.now().minus(SESSION_TTL))) {
            sessions.remove(sessionId);
            return empty;
        }
        return found;
    }

    private void remember(String sessionId, Long orderId, Long customerId, String code) {
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 100) {
            return;
        }
        if (orderId == null && customerId == null && code == null) {
            sessions.remove(sessionId);
            return;
        }
        if (sessions.size() >= MAX_SESSIONS) {
            Instant cutoff = Instant.now().minus(SESSION_TTL);
            sessions.values().removeIf(p -> p.at().isBefore(cutoff));
            if (sessions.size() >= MAX_SESSIONS) {
                sessions.clear();
            }
        }
        sessions.put(sessionId, new Pending(orderId, customerId, code, Instant.now()));
    }

    private String describe(OrderRepository.Order order) {
        String statusText = switch (order.status()) {
            case "PLACED" -> "has been placed and is being prepared";
            case "SHIPPED" -> "has been shipped and is on its way";
            case "DELIVERED" -> "has been delivered";
            case "CANCELLED" -> "was cancelled";
            default -> "has the status " + order.status();
        };
        return "Your order " + order.id() + " " + statusText + ". It was placed on "
                + order.createdAt().toLocalDate() + " and the total is Rs. "
                + order.totalAmount().toPlainString() + ".";
    }

    /** Returns the matching FAQ text, or null when nothing relevant was found. */
    private String findContext(String message) {
        List<Document> matches = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(3)
                        .similarityThreshold(0.5)
                        .build());

        if (matches.isEmpty()) {
            return null;
        }
        return matches.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
    }

    private String buildPrompt(String context, String message) {
        return """
                Answer the customer question using ONLY the context below.
                If the context does not clearly answer the question, reply exactly:
                %s

                Context:
                %s

                Customer question: %s
                """.formatted(FALLBACK, context, message);
    }

    private void requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be empty");
        }
    }

    public record ChatRequest(String message, String sessionId) {
    }

    public record ChatResponse(String reply) {
    }
}