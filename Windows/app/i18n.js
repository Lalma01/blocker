'use strict';
// Shared renderer translations for PS-BLOCK (English / Hungarian).
(function () {
  const DICT = {
    hu: {
      // index
      app_name: 'PS-BLOCK',
      idx_sub: 'Aktívan védi a böngészőt a nem kívánt tartalmaktól',
      lbl_status: 'Állapot', lbl_blocked: 'Blokkolt oldalak', lbl_pw: 'Jelszóvédelem',
      lbl_screentime: 'Képernyőidő', lbl_autostart: 'Automatikus indítás', lbl_custom: 'Egyéni szűrők',
      val_active: 'Aktív', val_pw_set: 'Beállítva', val_pw_none: 'Nincs',
      val_unlimited: 'Korlátlan', val_expired: 'Lejárt!', val_on: 'Bekapcsolva', val_off: 'Kikapcsolva',
      unit_items: 'db', btn_settings: '⚙ Beállítások', btn_screentime: '⏱ Képernyőidő',
      footer_text: 'Minden szűrés helyi · Nincs adatküldés',
      // settings
      set_title: 'Beállítások – PS-BLOCK', set_h1: 'Beállítások',
      theme_h2: 'Megjelenés', theme_label: 'Téma:',
      theme_system: 'Rendszer szerint (automatikus)', theme_light: 'Világos', theme_dark: 'Sötét',
      lock_h2: 'Jelszóvédett beállítások', lock_ph: 'Írd be a jelszót', lock_btn: 'Feloldás', lock_wrong: 'Hibás jelszó!',
      pw_h2: 'Jelszóvédelem', pw_newlabel: 'Új jelszó (min. 4 karakter):', pw_ph: 'Jelszó',
      pw_confirmlabel: 'Megerősítés:', pw_confirm_ph: 'Jelszó megerősítése',
      pw_setbtn: 'Jelszó beállítása', pw_delbtn: 'Jelszó törlése',
      pw_min_msg: 'Min. 4 karakter!', pw_nomatch_msg: 'A két jelszó nem egyezik!',
      pw_prompt_old: 'Add meg a jelenlegi jelszót a módosításhoz:', pw_set_ok: 'Jelszó sikeresen beállítva!',
      pw_err: 'Hiba történt!', pw_del_prompt: 'Add meg a jelenlegi jelszót a törléshez:', pw_deleted: 'Jelszó törölve.',
      time_h2: 'Képernyőidő-korlát', time_label: 'Napi limit (perc, 0 = korlátlan):', btn_save: 'Mentés',
      time_prompt_pw: 'Add meg a jelszót a képernyőidő-korlát módosításához:', msg_saved: 'Mentve!', msg_err: 'Hiba: ',
      auto_h2: 'Automatikus indítás', auto_label: 'Induljon el Windows indításakor',
      block_h2: 'Egyéni tiltólista', block_label: 'Domain vagy kulcsszó hozzáadása:',
      block_ph: 'pl. example.com vagy kulcsszó', block_add: 'Hozzáad',
      block_exists: 'Már szerepel a listán!', block_added: 'Hozzáadva!',
      lang_h2: 'Nyelv / Language', lang_label: '🌐 Felület nyelve / Interface language:', close_btn: 'Bezárás',
      // screentime
      st_title: 'Képernyőidő – PS-BLOCK', st_h1: 'Képernyőidő', st_sub: 'Napi képernyőidő-korlát',
      ring_left: 'maradt', ring_unlimited: 'korlátlan', card_used: 'Felhasznált', card_limit: 'Napi limit',
      limit_msg: '⚠ A napi képernyőidő-korlát elérve!', add_btn: '+ 15 perc hozzáadása',
      st_lock_h2: 'Jelszóvédett művelet', st_lock_p: 'Add meg a jelszót az idő hozzáadásához',
      st_lock_cancel: 'Mégse', unit_min: 'perc', err_generic: 'Hiba történt!',
      lock_hint: 'A gép zárolva van. A folytatáshoz adj hozzá időt a jelszóval.',
      // blocked
      bl_title: 'Tartalom blokkolva', bl_h1: 'Tartalom blokkolva',
      bl_p: 'A kért weboldal nem felel meg a PS-BLOCK tartalomszűrési házirendjének, ezért a hozzáférést letiltottuk.',
      bl_shield: 'PS-BLOCK · aktív védelem',
      // notification
      nt_title: 'TARTALOM BLOKKOLVA', nt_body: 'A böngésző tiltott tartalmat nyitott meg.', nt_reason: 'Indok: ',
      u_h: 'ó', u_m: 'p', u_s: 'mp', dur_h: 'óra', dur_m: 'perc',
    },
    en: {
      app_name: 'PS-BLOCK',
      idx_sub: 'Actively protects your browser from unwanted content',
      lbl_status: 'Status', lbl_blocked: 'Blocked sites', lbl_pw: 'Password protection',
      lbl_screentime: 'Screen time', lbl_autostart: 'Auto-start', lbl_custom: 'Custom filters',
      val_active: 'Active', val_pw_set: 'Enabled', val_pw_none: 'None',
      val_unlimited: 'Unlimited', val_expired: 'Expired!', val_on: 'Enabled', val_off: 'Disabled',
      unit_items: 'pcs', btn_settings: '⚙ Settings', btn_screentime: '⏱ Screen Time',
      footer_text: 'All filtering is local · No data is sent',
      set_title: 'Settings – PS-BLOCK', set_h1: 'Settings',
      theme_h2: 'Appearance', theme_label: 'Theme:',
      theme_system: 'Match system (automatic)', theme_light: 'Light', theme_dark: 'Dark',
      lock_h2: 'Password-protected settings', lock_ph: 'Enter password', lock_btn: 'Unlock', lock_wrong: 'Wrong password!',
      pw_h2: 'Password protection', pw_newlabel: 'New password (min. 4 characters):', pw_ph: 'Password',
      pw_confirmlabel: 'Confirmation:', pw_confirm_ph: 'Confirm password',
      pw_setbtn: 'Set password', pw_delbtn: 'Remove password',
      pw_min_msg: 'Minimum 4 characters!', pw_nomatch_msg: 'The two passwords do not match!',
      pw_prompt_old: 'Enter the current password to change it:', pw_set_ok: 'Password set successfully!',
      pw_err: 'An error occurred!', pw_del_prompt: 'Enter the current password to remove it:', pw_deleted: 'Password removed.',
      time_h2: 'Screen time limit', time_label: 'Daily limit (minutes, 0 = unlimited):', btn_save: 'Save',
      time_prompt_pw: 'Enter the password to change the screen time limit:', msg_saved: 'Saved!', msg_err: 'Error: ',
      auto_h2: 'Auto-start', auto_label: 'Start with Windows',
      block_h2: 'Custom blocklist', block_label: 'Add a domain or keyword:',
      block_ph: 'e.g. example.com or keyword', block_add: 'Add',
      block_exists: 'Already on the list!', block_added: 'Added!',
      lang_h2: 'Language / Nyelv', lang_label: '🌐 Interface language / Felület nyelve:', close_btn: 'Close',
      st_title: 'Screen Time – PS-BLOCK', st_h1: 'Screen Time', st_sub: 'Daily screen time limit',
      ring_left: 'left', ring_unlimited: 'unlimited', card_used: 'Used', card_limit: 'Daily limit',
      limit_msg: '⚠ Daily screen time limit reached!', add_btn: '+ Add 15 minutes',
      st_lock_h2: 'Password-protected action', st_lock_p: 'Enter the password to add time',
      st_lock_cancel: 'Cancel', unit_min: 'min', err_generic: 'An error occurred!',
      lock_hint: 'The computer is locked. To continue, add time with the password.',
      bl_title: 'Content blocked', bl_h1: 'Content blocked',
      bl_p: 'The requested website does not meet the PS-BLOCK content policy, so access has been blocked.',
      bl_shield: 'PS-BLOCK · active protection',
      nt_title: 'CONTENT BLOCKED', nt_body: 'The browser opened blocked content.', nt_reason: 'Reason: ',
      u_h: 'h', u_m: 'm', u_s: 's', dur_h: 'h', dur_m: 'min',
    },
  };

  let lang = 'hu';
  window.setLang = (l) => { lang = (l === 'en') ? 'en' : 'hu'; };
  window.t = (k) => (DICT[lang] && DICT[lang][k] !== undefined) ? DICT[lang][k] : (DICT.hu[k] !== undefined ? DICT.hu[k] : k);
  // Single consistent duration format used everywhere: always "H óra M perc" /
  // "H h M min" (both units shown). Negative = unlimited.
  window.fmtDuration = (sec) => {
    if (sec < 0) return window.t('val_unlimited');
    sec = Math.max(0, Math.floor(sec));
    const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60);
    return `${h} ${window.t('dur_h')} ${m} ${window.t('dur_m')}`;
  };
  window.applyI18n = () => {
    document.documentElement.lang = lang;
    document.querySelectorAll('[data-i18n]').forEach(el => { el.textContent = window.t(el.getAttribute('data-i18n')); });
    document.querySelectorAll('[data-i18n-ph]').forEach(el => { el.placeholder = window.t(el.getAttribute('data-i18n-ph')); });
    const tEl = document.querySelector('[data-i18n-title]');
    if (tEl) document.title = window.t(tEl.getAttribute('data-i18n-title'));
  };
})();
