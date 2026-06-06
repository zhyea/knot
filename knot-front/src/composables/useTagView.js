import { nextTick, ref } from "vue";

const visitedTags = ref([]);
const cachedViewNames = ref([]);
const refreshMarks = ref({});
const affixInitialized = ref(false);

function buildTagKey(targetRoute) {
  return targetRoute?.fullPath || targetRoute?.path || "";
}

function resolveTagTitle(targetRoute) {
  return targetRoute?.meta?.title || targetRoute?.name || targetRoute?.path || "未命名页面";
}

function resolveCacheName(targetRoute) {
  return targetRoute?.meta?.cacheName || null;
}

function shouldDisplayTag(targetRoute) {
  return !!targetRoute?.path && !targetRoute?.meta?.hiddenTagView;
}

function shouldCacheView(targetRoute) {
  return shouldDisplayTag(targetRoute) && !targetRoute?.meta?.noCache && !!resolveCacheName(targetRoute);
}

function createTag(targetRoute) {
  return {
    key: buildTagKey(targetRoute),
    name: targetRoute?.name || null,
    path: targetRoute?.path || "",
    fullPath: targetRoute?.fullPath || targetRoute?.path || "",
    title: resolveTagTitle(targetRoute),
    affix: !!targetRoute?.meta?.affix,
    cacheName: resolveCacheName(targetRoute)
  };
}

function syncCachedViews() {
  const names = visitedTags.value
    .filter((tag) => tag.cacheName)
    .map((tag) => tag.cacheName);
  cachedViewNames.value = [...new Set(names)];
}

function dedupeAffix() {
  const seen = new Set();
  visitedTags.value = visitedTags.value.filter((item) => {
    if (!item.affix) {
      return true;
    }
    if (seen.has(item.key)) {
      return false;
    }
    seen.add(item.key);
    return true;
  });
}

function initializeAffixTags(router) {
  if (affixInitialized.value || !router) {
    return;
  }

  affixInitialized.value = true;
  router.getRoutes()
    .filter((item) => item.meta?.affix && shouldDisplayTag(item))
    .sort((a, b) => a.path.localeCompare(b.path))
    .forEach((item) => {
      addVisitedTag({
        path: item.path,
        fullPath: item.path,
        name: item.name,
        meta: item.meta
      });
    });
}

function addVisitedTag(targetRoute) {
  if (!shouldDisplayTag(targetRoute)) {
    return null;
  }

  const tag = createTag(targetRoute);
  const exists = visitedTags.value.findIndex((item) => item.key === tag.key);
  if (exists >= 0) {
    visitedTags.value.splice(exists, 1, tag);
  } else if (tag.affix) {
    visitedTags.value.unshift(tag);
    dedupeAffix();
  } else {
    visitedTags.value.push(tag);
  }

  syncCachedViews();
  return tag;
}

function ensureRouteTag(targetRoute, router) {
  initializeAffixTags(router);
  return addVisitedTag(targetRoute);
}

function findFallbackTag(removedIndex) {
  return visitedTags.value[removedIndex] || visitedTags.value[removedIndex - 1] || visitedTags.value[0] || null;
}

function removeTag(tag, activeKey) {
  if (!tag || tag.affix) {
    return { fallbackTag: null, removed: false };
  }

  const index = visitedTags.value.findIndex((item) => item.key === tag.key);
  if (index < 0) {
    return { fallbackTag: null, removed: false };
  }

  const isActive = tag.key === activeKey;
  visitedTags.value.splice(index, 1);
  syncCachedViews();

  return {
    removed: true,
    fallbackTag: isActive ? findFallbackTag(index) : null
  };
}

function removeOtherTags(currentTag) {
  if (!currentTag) {
    return null;
  }

  visitedTags.value = visitedTags.value.filter((item) => item.affix || item.key === currentTag.key);
  syncCachedViews();
  return visitedTags.value.find((item) => item.key === currentTag.key) || visitedTags.value[0] || null;
}

function removeAllTags() {
  visitedTags.value = visitedTags.value.filter((item) => item.affix);
  syncCachedViews();
  return visitedTags.value[0] || null;
}

async function refreshTag(targetRoute) {
  const cacheName = targetRoute?.cacheName || resolveCacheName(targetRoute);
  const routeKey = targetRoute?.key || buildTagKey(targetRoute);

  if (cacheName) {
    cachedViewNames.value = cachedViewNames.value.filter((item) => item !== cacheName);
    await nextTick();
  }

  refreshMarks.value = {
    ...refreshMarks.value,
    [routeKey]: Date.now()
  };

  if (cacheName) {
    cachedViewNames.value = [...new Set([...cachedViewNames.value, cacheName])];
  }
}

function resolveViewKey(targetRoute) {
  const routeKey = buildTagKey(targetRoute);
  const refreshMark = refreshMarks.value[routeKey] || 0;
  return `${routeKey}::${refreshMark}`;
}

export function useTagView() {
  return {
    visitedTags,
    cachedViewNames,
    ensureRouteTag,
    removeTag,
    removeOtherTags,
    removeAllTags,
    refreshTag,
    resolveViewKey,
    shouldCacheView,
    buildTagKey
  };
}
