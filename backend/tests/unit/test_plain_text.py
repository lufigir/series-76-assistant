import pytest

from src.agent.plain_text import plain_text


@pytest.mark.parametrize(
    ("answer", "expected"),
    [
        ("The smallest is **763B00-11300000/A**.", "The smallest is 763B00-11300000/A."),
        ("It gives __80 Nm__ on/off.", "It gives 80 Nm on/off."),
        ("Use `761A00-11300000/A`.", "Use 761A00-11300000/A."),
        ("No model reaches 9000 Nm.", "No model reaches 9000 Nm."),
        ("Duty is 40% * 2 runs.", "Duty is 40% * 2 runs."),
    ],
)
def test_the_answer_reaches_the_app_as_plain_text(answer: str, expected: str) -> None:
    assert plain_text(answer) == expected
