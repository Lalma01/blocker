package com.psblock.app

/**
 * Domain & keyword lists ported verbatim from the Windows build (main.js).
 * BUILTIN_DOMAINS are blocked at the DNS layer; ALLOWED_DOMAINS are never blocked.
 * STRONG/WEAK/WHITELIST keywords drive the browser-title monitor.
 */
object Blocklist {

    val BUILTIN_DOMAINS = listOf(
        // NSFW Porn
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
        "youporn.com", "tube8.com", "beeg.com", "spankbang.com", "eporner.com",
        "brazzers.com", "bangbros.com", "naughtyamerica.com", "chaturbate.com",
        "cam4.com", "myfreecams.com", "livejasmin.com", "stripchat.com",
        "bongacams.com", "onlyfans.com", "nhentai.net", "hentaihaven.xxx",
        "e621.net", "gelbooru.com", "rule34.xxx", "danbooru.donmai.us",
        "jerkmate.com", "camsoda.com", "flirt4free.com", "adulttime.com",
        "realitykings.com", "mofos.com", "tnaflix.com", "porndig.com",
        "drtuber.com", "slutload.com", "xtube.com", "pornmd.com", "keezmovies.com",
        // NSFW AI Chat
        "spicychat.ai", "crushon.ai", "janitorai.com", "candy.ai", "muah.ai",
        "dreamgf.ai", "dreambf.ai", "erogen.ai", "intimateai.com",
        "character.ai", "naughtydog.ai", "kindroid.ai", "soulgen.ai", "venus.chub.ai",
        // NSFW AI Image generators — only tools explicitly built for NSFW content
        // (undressing/deepfake apps, adult model hubs). General-purpose, mainstream
        // image generators (Midjourney, Canva, etc.) are NOT NSFW tools and must stay
        // reachable — see ALLOWED_DOMAINS / WHITELIST_AI_KEYWORDS below.
        "civitai.com", "pornpen.ai", "aiporncreator.ai", "nudify.online",
        "seduced.ai", "promptchan.ai", "undress.app", "deepnude.cc", "nsfwgenerator.ai",
        "replika.com", "yodayo.com"
    )

    // Mainstream AI assistants and general-purpose creative tools that must stay
    // accessible (never blocked), even though some STRONG_KEYWORDS below (if left
    // unrestricted) could otherwise match them.
    val ALLOWED_DOMAINS = listOf(
        "grok.com", "claude.ai", "perplexity.ai", "gemini.google.com", "x.com",
        "chatgpt.com", "chat.openai.com", "openai.com", "copilot.microsoft.com",
        "poe.com", "you.com", "pi.ai", "bard.google.com",
        // Mainstream, non-NSFW AI image generators / creative suites.
        "canva.com", "perchance.org", "midjourney.com", "leonardo.ai",
        "nightcafe.studio", "lexica.art", "playgroundai.com", "craiyon.com",
        "stablediffusionweb.com", "mage.space", "tensor.art", "runwayml.com",
        "ideogram.ai", "krea.ai", "designer.microsoft.com"
    )

    // Strong keywords: a single match blocks (unambiguous porn / NSFW-AI brands & terms).
    // Deliberately excludes generic terms like "image generator" and brand names of
    // mainstream, non-NSFW tools (Midjourney, Canva, Leonardo, …) — those caused false
    // positives (e.g. blocking this project's own GitHub page, or Canva's own "image
    // generator" feature) without adding any real NSFW protection.
    val STRONG_KEYWORDS = listOf(
        "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "onlyfans",
        "chaturbate", "spankbang", "stripchat", "spicychat", "crushon", "janitorai",
        "character.ai", "nudify", "pornpen", "civitai", "nhentai", "rule34",
        "porn", "xxx", "nsfw", "hentai", "live sex", "sex chat", "adult content",
        "replika", "candy.ai", "dreamgf", "dreambf", "muah.ai", "soulgen", "kindroid",
        "deepnude", "undress", "seduced.ai", "promptchan", "aiporncreator", "nsfwgenerator"
    )

    // Weak/ambiguous keywords: block only when at least two appear in the same title.
    val WEAK_KEYWORDS = listOf(
        "sex", "sexy", "nude", "naked", "dating", "hookup", "free date", "meet singles",
        "meet girls", "hot women", "escort", "sugar daddy", "megismerkedés"
    )

    // Titles containing any of these are never blocked: mainstream AI assistants,
    // mainstream image-generator brands, and reference/dev sites whose pages can
    // legitimately mention the very words used to build the blocklists above (e.g.
    // this project's own GitHub page/description, or a Wikipedia article about the
    // topic) without actually being adult content.
    val WHITELIST_AI_KEYWORDS = listOf(
        "grok", "claude", "perplexity", "gemini", "chatgpt", "copilot", "openai", "pi.ai", "you.com",
        "canva", "midjourney", "leonardo.ai", "nightcafe", "lexica", "playground ai", "craiyon",
        "stable diffusion", "mage.space", "tensor.art", "runwayml", "ideogram", "krea.ai", "perchance",
        "github", "wikipedia", "stack overflow"
    )

    // ── DoH / DoT hardening ────────────────────────────────────────────────
    // Hostnames of well-known DNS-over-HTTPS / DNS-over-TLS providers. Resolving these
    // (the "bootstrap" lookup) is blocked so apps can't discover an encrypted resolver.
    val DOH_DOT_HOSTS = listOf(
        "dns.google", "dns.google.com", "cloudflare-dns.com", "mozilla.cloudflare-dns.com",
        "one.one.one.one", "dns.quad9.net", "dns9.quad9.net", "dns.adguard.com",
        "dns.adguard-dns.com", "dns-family.adguard.com", "doh.opendns.com",
        "doh.familyshield.opendns.com", "dns.nextdns.io", "doh.cleanbrowsing.org",
        "doh.mullvad.net", "dns.controld.com", "freedns.controld.com", "doh.dns.sb",
        "dot.dns.sb", "dns.alidns.com", "doh.pub", "dns.twnic.tw", "doh.libredns.gr",
        "anycast.censurfridns.dk", "dnsforge.de", "dns.digitale-gesellschaft.ch",
        "chromium-dns.com", "doh.appliedprivacy.net"
    )

    // IPv4 anycast addresses of the major encrypted resolvers. Connections to these are
    // black-holed by the VPN so hard-coded DoH/DoT clients fail and fall back to plain DNS.
    val DOH_DOT_IPS = listOf(
        "1.1.1.1", "1.0.0.1", "1.1.1.2", "1.0.0.2", "1.1.1.3", "1.0.0.3",
        "8.8.8.8", "8.8.4.4",
        "9.9.9.9", "149.112.112.112", "9.9.9.11", "149.112.112.11", "9.9.9.10", "149.112.112.10",
        "94.140.14.14", "94.140.15.15", "94.140.14.15", "94.140.15.16",
        "208.67.222.222", "208.67.220.220", "208.67.222.123", "208.67.220.123",
        "185.228.168.9", "185.228.169.9", "185.228.168.168", "185.228.169.168",
        "76.76.2.0", "76.76.10.0", "76.76.2.2", "76.76.10.2",
        "194.242.2.2", "194.242.2.3", "194.242.2.4",
        "45.90.28.0", "45.90.30.0",
        "146.112.41.2"
    )

    // IPv6 addresses of the same providers.
    val DOH_DOT_IPS6 = listOf(
        "2606:4700:4700::1111", "2606:4700:4700::1001",
        "2606:4700:4700::1112", "2606:4700:4700::1002",
        "2001:4860:4860::8888", "2001:4860:4860::8844",
        "2620:fe::fe", "2620:fe::9",
        "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff",
        "2620:119:35::35", "2620:119:53::53"
    )

    /** Returns true if [domain] (or a parent of it) is on the allow list. */
    fun isAllowed(domain: String): Boolean {
        val d = domain.lowercase().removePrefix("www.")
        return ALLOWED_DOMAINS.any { d == it || d.endsWith(".$it") }
    }

    /**
     * The full effective block set for DNS: builtin + custom, minus anything on the allow list.
     * Mirrors updateHosts() filtering in main.js.
     */
    fun effectiveDomains(custom: Collection<String>): Set<String> {
        val all = LinkedHashSet<String>()
        all.addAll(BUILTIN_DOMAINS)
        // Custom entries that look like domains (contain a dot, no spaces) act as DNS blocks too.
        all.addAll(custom.filter { it.contains('.') && !it.contains(' ') })
        return all.filterNot { isAllowed(it) }.toSet()
    }
}
