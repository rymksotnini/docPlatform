import { Component, Output, EventEmitter, OnInit } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { DoctorService } from 'app/entities/doctor';
import { HttpResponse } from '@angular/common/http';
import { AccountService, User } from 'app/core';
import { Request } from 'app/shared/model/request.model';
import { Patient } from 'app/shared/model/patient.model';
import { AppointmentService } from 'app/entities/appointment';
import { JhiDataUtils } from 'ng-jhipster';

@Component({
    selector: 'jhi-sidebar',
    templateUrl: './sidebar.component.html',
    styleUrls: ['./_sidebar.scss']
})
export class SidebarComponent implements OnInit {
    isActive: boolean;
    collapsed: boolean;
    showMenu: string;
    pushRightClass: string;
    image: any;
    MyVar = false;
    name: string;
    pictureContentType: any;
    id: any;

    @Output() collapsedEvent = new EventEmitter<boolean>();

    constructor(
        private translate: TranslateService,
        public router: Router,
        protected doctorService: DoctorService,
        protected dataUtils: JhiDataUtils,
        private appointmentService: AppointmentService,
        private accountService: AccountService
    ) {
        this.router.events.subscribe(val => {
            if (val instanceof NavigationEnd && window.innerWidth <= 992 && this.isToggled()) {
                this.toggleSidebar();
            }
        });
    }

    ngOnInit() {
        this.name = '';
        this.isActive = false;
        this.collapsed = false;
        this.showMenu = '';
        this.pushRightClass = 'push-right';
        this.appointmentService.getCurrentUser().subscribe((res: HttpResponse<User>) => {
            console.log('login:' + res.body.login);
            this.name = res.body.login;
        });

        console.log('login:' + this.name);
        this.doctorService.getCurrentUser().subscribe((res: HttpResponse<Patient>) => {
            this.image = res.body.picture;
            this.pictureContentType = res.body.pictureContentType;
            this.MyVar = true;
            this.id = res.body.id;
            console.log(this.id);

            console.log('image problem !!');
        });
    }

    eventCalled() {
        this.isActive = !this.isActive;
    }

    addExpandClass(element: any) {
        if (element === this.showMenu) {
            this.showMenu = '0';
        } else {
            this.showMenu = element;
        }
    }

    toggleCollapsed() {
        this.collapsed = !this.collapsed;
        this.collapsedEvent.emit(this.collapsed);
    }

    isToggled(): boolean {
        const dom: Element = document.querySelector('body');
        return dom.classList.contains(this.pushRightClass);
    }

    toggleSidebar() {
        const dom: any = document.querySelector('body');
        dom.classList.toggle(this.pushRightClass);
    }

    rltAndLtr() {
        const dom: any = document.querySelector('body');
        dom.classList.toggle('rtl');
    }

    changeLang(language: string) {
        this.translate.use(language);
    }
    byteSize(field) {
        return this.dataUtils.byteSize(field);
    }

    onLoggedout() {
        localStorage.removeItem('isLoggedin');
    }

    isAuthenticated() {
        return this.accountService.isAuthenticated();
    }
}
