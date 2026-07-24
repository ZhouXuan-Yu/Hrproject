from datetime import datetime


def test_reject_email_uses_latest_db_template(app, monkeypatch):
    from app.extensions import db
    from app.models.auxiliary import NotifyTemplate
    from app.models.candidate import Candidate, Resume
    from app.models.demand import RecruitDemand
    from app.models.interview import InterviewBook
    from app.services.confirm_service import send_interview_reject_email

    monkeypatch.setenv('COMPANY_NAME', 'ACME Corp')
    sent = {}

    def fake_send_mail(to, subject, html_body, **kwargs):
        sent.update({
            'to': to,
            'subject': subject,
            'html': html_body,
            'kwargs': kwargs,
        })
        return True, 'sent'

    monkeypatch.setattr('app.services.mail_sender.send_mail', fake_send_mail)

    with app.app_context():
        candidate = Candidate(
            candidate_no='C-TPL-001',
            candidate_name='Alice',
            email='alice@example.com',
            edu_level=2,
            school_level=1,
            work_years=3,
        )
        db.session.add(candidate)
        db.session.flush()

        resume = Resume(
            candidate_id=candidate.id,
            storage_time=datetime(2026, 7, 24, 10, 0),
            base_score=60,
        )
        demand = RecruitDemand(
            demand_no='DM-TPL-001',
            dept_id=1,
            position_id=1,
            position_name='Backend Engineer',
            recruit_type=1,
            plan_headcount=1,
            creator_id=1,
            demand_status=2,
        )
        db.session.add_all([resume, demand])
        db.session.flush()

        book = InterviewBook(
            demand_id=demand.id,
            resume_id=resume.id,
            process_id=0,
            slot_id=0,
            interview_round=1,
            interview_type=1,
            book_time=datetime(2026, 8, 1, 10, 0),
        )
        template = NotifyTemplate(
            template_name='Reject notice',
            template_type='reject',
            send_method='email',
            subject='Reject {{position}} for {{candidate}}',
            body='Hello {{candidate}}, {{company}} will not continue {{position}}. {{comment}}',
            status=1,
        )
        db.session.add_all([book, template])
        db.session.commit()

        ok, msg = send_interview_reject_email(book, 'Skill depth did not match.')

    assert ok is True
    assert msg == 'sent'
    assert sent['to'] == 'alice@example.com'
    assert sent['subject'] == 'Reject Backend Engineer for Alice'
    assert 'Hello Alice' in sent['html']
    assert 'ACME Corp will not continue Backend Engineer' in sent['html']
    assert 'Skill depth did not match.' in sent['html']
    assert sent['kwargs']['mail_type'] == 'reject'
