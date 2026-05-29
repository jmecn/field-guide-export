package io.github.jmecn.fieldguideexport.export.resources;

import io.github.jmecn.fieldguideexport.export.patchouli.Book;
import io.github.jmecn.fieldguideexport.export.scan.BookScanResult;

import java.util.Set;
import java.util.TreeSet;

/** Namespaces touched by a Patchouli book closure (for filtered lang merge). */
public final class ClosureNamespaces {

    private ClosureNamespaces() {}

    public static Set<String> from(Book book, BookScanResult scan, Set<String> extraItemOrRecipeIds) {
        return from(book, scan, extraItemOrRecipeIds, null);
    }

    public static Set<String> from(
            Book book,
            BookScanResult scan,
            Set<String> extraItemOrRecipeIds,
            Set<String> langKeys) {
        Set<String> ns = new TreeSet<>();
        addId(ns, book.getNamespace());
        addFromRefs(ns, scan.getItems());
        addFromRefs(ns, scan.getRecipes());
        addFromRefs(ns, scan.getTextures());
        addFromRefs(ns, scan.getEntities());
        addFromRefs(ns, scan.getMultiblocks());
        addFromRefs(ns, scan.getModels());
        addFromRefs(ns, scan.getBlockstateRefs());
        if (extraItemOrRecipeIds != null) {
            addFromRefs(ns, extraItemOrRecipeIds);
        }
        if (langKeys != null) {
            addFromLangKeys(ns, langKeys);
        }
        ns.add("minecraft");
        ns.add("patchouli");
        ns.add("tfc");
        return ns;
    }

    /** Ensures namespaces referenced by collected lang keys (e.g. {@code item.gtceu.foo}) are included. */
    private static void addFromLangKeys(Set<String> ns, Iterable<String> langKeys) {
        for (String key : langKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            int first = key.indexOf('.');
            if (first <= 0 || first >= key.length() - 1) {
                continue;
            }
            int second = key.indexOf('.', first + 1);
            if (second > first + 1) {
                ns.add(key.substring(first + 1, second));
            } else {
                ns.add(key.substring(0, first));
            }
        }
    }

    private static void addFromRefs(Set<String> ns, Iterable<String> refs) {
        for (String ref : refs) {
            addId(ns, ref);
        }
    }

    private static void addId(Set<String> ns, String ref) {
        if (ref == null || ref.isBlank()) {
            return;
        }
        String s = ref;
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        int colon = s.indexOf(':');
        if (colon > 0) {
            ns.add(s.substring(0, colon));
        }
    }
}
