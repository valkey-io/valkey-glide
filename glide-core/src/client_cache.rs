use std::collections::HashMap;
use std::sync::{Arc, RwLock};
use std::time::{Duration, Instant};
use redis::Value;

#[derive(Debug, Clone)]
pub struct ClientCacheConfig {
    pub enabled: bool,
    pub max_size: usize,
    pub ttl_seconds: Option<u64>,
    pub tracking_mode: TrackingMode,
}

#[derive(Debug, Clone, Copy)]
pub enum TrackingMode {
    Default = 0,
    OptIn = 1,
    OptOut = 2,
}

impl Default for ClientCacheConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            max_size: 1000,
            ttl_seconds: None,
            tracking_mode: TrackingMode::Default,
        }
    }
}

#[derive(Debug, Clone)]
struct CacheEntry {
    value: Value,
    inserted_at: Instant,
    ttl: Option<Duration>,
}

pub struct ClientCache {
    storage: Arc<RwLock<HashMap<String, CacheEntry>>>,
    config: ClientCacheConfig,
}

impl ClientCache {
    pub fn new(config: ClientCacheConfig) -> Self {
        Self {
            storage: Arc::new(RwLock::new(HashMap::new())),
            config,
        }
    }

    pub fn get(&self, key: &str) -> Option<Value> {
        let storage = self.storage.read().ok()?;
        let entry = storage.get(key)?;
        
        // Check TTL
        if let Some(ttl) = entry.ttl {
            if entry.inserted_at.elapsed() > ttl {
                drop(storage);
                self.invalidate_key(key);
                return None;
            }
        }
        
        Some(entry.value.clone())
    }

    pub fn set(&self, key: String, value: Value) {
        if !self.config.enabled {
            return;
        }

        let mut storage = self.storage.write().unwrap();
        
        // Evict if at capacity
        if storage.len() >= self.config.max_size {
            self.evict_lru(&mut storage);
        }

        let ttl = self.config.ttl_seconds.map(Duration::from_secs);
        storage.insert(key, CacheEntry {
            value,
            inserted_at: Instant::now(),
            ttl,
        });
    }

    pub fn invalidate(&self, keys: &[String]) {
        let mut storage = self.storage.write().unwrap();
        for key in keys {
            storage.remove(key);
        }
    }

    pub fn invalidate_key(&self, key: &str) {
        let mut storage = self.storage.write().unwrap();
        storage.remove(key);
    }

    pub fn clear(&self) {
        let mut storage = self.storage.write().unwrap();
        storage.clear();
    }

    fn evict_lru(&self, storage: &mut HashMap<String, CacheEntry>) {
        if let Some((oldest_key, _)) = storage
            .iter()
            .min_by_key(|(_, entry)| entry.inserted_at)
            .map(|(k, v)| (k.clone(), v.clone()))
        {
            storage.remove(&oldest_key);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_cache_basic_operations() {
        let config = ClientCacheConfig {
            enabled: true,
            max_size: 2,
            ttl_seconds: None,
            tracking_mode: TrackingMode::Default,
        };
        
        let cache = ClientCache::new(config);
        
        // Test set and get
        cache.set("key1".to_string(), Value::BulkString(b"value1".to_vec()));
        assert!(cache.get("key1").is_some());
        
        // Test cache miss
        assert!(cache.get("nonexistent").is_none());
        
        // Test invalidation
        cache.invalidate(&["key1".to_string()]);
        assert!(cache.get("key1").is_none());
    }

    #[test]
    fn test_cache_eviction() {
        let config = ClientCacheConfig {
            enabled: true,
            max_size: 2,
            ttl_seconds: None,
            tracking_mode: TrackingMode::Default,
        };
        
        let cache = ClientCache::new(config);
        
        // Fill cache to capacity
        cache.set("key1".to_string(), Value::BulkString(b"value1".to_vec()));
        cache.set("key2".to_string(), Value::BulkString(b"value2".to_vec()));
        
        // Add one more - should evict oldest
        cache.set("key3".to_string(), Value::BulkString(b"value3".to_vec()));
        
        // key1 should be evicted, key2 and key3 should remain
        assert!(cache.get("key1").is_none());
        assert!(cache.get("key2").is_some());
        assert!(cache.get("key3").is_some());
    }

    #[test]
    fn test_cache_disabled() {
        let config = ClientCacheConfig {
            enabled: false,
            max_size: 10,
            ttl_seconds: None,
            tracking_mode: TrackingMode::Default,
        };
        
        let cache = ClientCache::new(config);
        
        // Should not cache when disabled
        cache.set("key1".to_string(), Value::BulkString(b"value1".to_vec()));
        assert!(cache.get("key1").is_none());
    }
}
