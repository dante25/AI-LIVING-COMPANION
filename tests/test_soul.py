import unittest

from soul import MOOD_HOME, SoulState, decide, on_event, rest, tick


class StateTests(unittest.TestCase):
    def test_values_stay_in_range(self):
        s = SoulState(anger=95)
        for _ in range(10):
            on_event(s, "insult")
        self.assertLessEqual(s.anger, 100)
        self.assertGreaterEqual(s.mood, 0)

    def test_insult_raises_anger_and_lowers_mood(self):
        s = SoulState()
        anger, mood = s.anger, s.mood
        on_event(s, "insult")
        self.assertGreater(s.anger, anger)
        self.assertLess(s.mood, mood)

    def test_praise_calms_anger(self):
        s = SoulState(anger=50)
        on_event(s, "praise")
        self.assertLess(s.anger, 50)

    def test_anger_fades_and_mood_returns_home(self):
        s = SoulState(mood=20, anger=80)
        tick(s, 240)
        self.assertEqual(s.anger, 0)
        self.assertLess(abs(s.mood - MOOD_HOME), 5)

    def test_boredom_grows_when_ignored(self):
        s = SoulState()
        boredom = s.boredom
        tick(s, 60)
        self.assertGreater(s.boredom, boredom)

    def test_rest_restores_energy(self):
        s = SoulState(energy=10)
        rest(s, 60)
        self.assertGreater(s.energy, 10)

    def test_same_input_same_result(self):
        a, b = SoulState(), SoulState()
        for kind in ("greeting", "insult", "joke"):
            on_event(a, kind)
            on_event(b, kind)
        self.assertEqual(a.as_dict(), b.as_dict())

    def test_unknown_event_is_rejected(self):
        with self.assertRaises(ValueError):
            on_event(SoulState(), "nepoznato")


class DecisionTests(unittest.TestCase):
    def test_normal_state_responds(self):
        self.assertEqual(decide(SoulState(), "chat").action, "respond")

    def test_low_energy_sleeps(self):
        self.assertEqual(decide(SoulState(energy=10), "chat").action, "sleep")

    def test_insult_gets_sulk(self):
        self.assertEqual(decide(SoulState(), "insult").action, "sulk")

    def test_high_anger_sulks_even_on_greeting(self):
        self.assertEqual(decide(SoulState(anger=65), "greeting").action, "sulk")

    def test_high_boredom_seeks_attention(self):
        self.assertEqual(decide(SoulState(boredom=75), "chat").action, "seek_attention")

    def test_ignored_for_hours_seeks_attention(self):
        s = SoulState()
        tick(s, 200)
        self.assertEqual(decide(s, "chat").action, "seek_attention")

    def test_playful_refusal_only_when_upset(self):
        self.assertEqual(
            decide(SoulState(), "command", refuse_playfully=True).action, "respond"
        )
        self.assertEqual(
            decide(SoulState(anger=50), "command", refuse_playfully=True).action,
            "refuse",
        )


if __name__ == "__main__":
    unittest.main()
