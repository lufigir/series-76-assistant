import pytest

import ingest


def test_a_saved_rebuild_needs_no_configuration(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("EXTRACTION_MODEL", raising=False)
    monkeypatch.delenv("EXTRACTION_VERIFY_MODEL", raising=False)

    models = ingest.extraction_models()

    assert len(models) == 2
    assert {model for model, _page in ingest.load_saved(models)} == set(models)


def test_the_environment_chooses_the_extraction_models(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("EXTRACTION_MODEL", "first/model")
    monkeypatch.setenv("EXTRACTION_VERIFY_MODEL", "second/model")

    assert ingest.extraction_models() == ["first/model", "second/model"]
