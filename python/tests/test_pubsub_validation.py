"""Tests for pubsub parameter validation."""

import pytest
from glide_shared.config import AdvancedGlideClientConfiguration


class TestPubSubValidation:
    """Test validation of pubsub-related parameters."""

    def test_pubsub_reconciliation_interval_zero_raises(self):
        """Test that zero reconciliation interval raises ValueError."""
        with pytest.raises(ValueError) as exc_info:
            AdvancedGlideClientConfiguration(pubsub_reconciliation_interval=0)
        assert "pubsub_reconciliation_interval must be positive" in str(exc_info.value)
        assert "got: 0" in str(exc_info.value)

    def test_pubsub_reconciliation_interval_negative_raises(self):
        """Test that negative reconciliation interval raises ValueError."""
        with pytest.raises(ValueError) as exc_info:
            AdvancedGlideClientConfiguration(pubsub_reconciliation_interval=-1)
        assert "pubsub_reconciliation_interval must be positive" in str(exc_info.value)
        assert "got: -1" in str(exc_info.value)

    def test_pubsub_reconciliation_interval_positive_succeeds(self):
        """Test that positive reconciliation interval is accepted."""
        config = AdvancedGlideClientConfiguration(pubsub_reconciliation_interval=1000)
        assert config.pubsub_reconciliation_interval == 1000

    def test_pubsub_reconciliation_interval_none_succeeds(self):
        """Test that None reconciliation interval is accepted."""
        config = AdvancedGlideClientConfiguration(pubsub_reconciliation_interval=None)
        assert config.pubsub_reconciliation_interval is None
