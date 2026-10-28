import importlib.util
import unittest
from pathlib import Path


spec = importlib.util.spec_from_file_location("panel_send_check", Path(__file__).with_name("panel_send_check.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class AllPanelEvidenceTests(unittest.TestCase):
    def test_old_summary_and_missing_backpack_log_cannot_pass(self):
        labels = ["Done on 3 of 3 panels", "✓ LED_BLE_one: Sent", "✓ LED_BLE_two: Sent",
                  "✓ iledcolor_backpack: Sent"]
        self.assertIsNone(module.verify_all(labels, chosen=False, seen_active=False,
                                            ipixel_logs=2, backpack_logs=0))
        self.assertIsNone(module.verify_all(labels, chosen=True, seen_active=False,
                                            ipixel_logs=2, backpack_logs=0))
        self.assertEqual("unverified", module.verify_all(labels, chosen=True, seen_active=True,
                                                          ipixel_logs=2, backpack_logs=0)[0])
        self.assertEqual("ok", module.verify_all(labels, chosen=True, seen_active=True,
                                                ipixel_logs=2, backpack_logs=1)[0])

    def test_already_present_is_reported_separately_from_a_new_transfer(self):
        labels = ["Hotovo na 2 z 2 panelů", "✓ LED_BLE_one: Odesláno",
                  "✓ iledcolor_backpack: Už byl v panelu (potvrzeno)"]
        outcome, _, rows = module.verify_all(labels, chosen=True, seen_active=True,
                                             ipixel_logs=1, backpack_logs=0)
        self.assertEqual("unverified", outcome)
        self.assertEqual("already_present", rows[1]["outcome"])

    def test_unavailable_backpack_keeps_confirmed_ipixel_results_as_partial(self):
        labels = ["Hotovo na 2 z 3 panelů", "✕ iledcolor_backpack: Není v dosahu",
                  "✓ LED_BLE_one: Odesláno", "✓ LED_BLE_two: Odesláno"]
        outcome, _, rows = module.verify_all(labels, chosen=True, seen_active=True,
                                             ipixel_logs=2, backpack_logs=0)
        self.assertEqual("partial", outcome)
        self.assertEqual(2, sum(row["outcome"] == "sent" for row in rows))


if __name__ == "__main__":
    unittest.main()
