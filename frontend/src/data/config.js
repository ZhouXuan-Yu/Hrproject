// data/config.js — 邮箱预设配置

export const EMAIL_PRESETS = {
  qq:     { server: 'imap.qq.com', port: '993', proto: 'IMAP（推荐）', ssl: 'SSL/TLS' },
  '163':  { server: 'imap.163.com', port: '993', proto: 'IMAP（推荐）', ssl: 'SSL/TLS' },
  gmail:  { server: 'imap.gmail.com', port: '993', proto: 'IMAP（推荐）', ssl: 'SSL/TLS' },
  corp:   { server: '', port: '', proto: 'IMAP（推荐）', ssl: 'SSL/TLS' },  // 企业邮箱不写死，靠自动检测
  custom: { server: '', port: '', proto: 'IMAP（推荐）', ssl: 'SSL/TLS' },
};
