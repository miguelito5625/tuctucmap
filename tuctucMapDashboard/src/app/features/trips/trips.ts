import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { SupabaseService } from '../../services/supabase.service';

@Component({
  selector: 'app-trips',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatCardModule,
    MatIconModule,
    MatChipsModule
  ],
  templateUrl: './trips.html',
  styleUrl: './trips.css',
})
export class Trips implements OnInit {
  private readonly supabaseService = inject(SupabaseService);
  
  displayedColumns: string[] = ['passengerName', 'driverName', 'message', 'status', 'created_at'];
  dataSource = new MatTableDataSource<any>([]);

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  async ngOnInit() {
    await this.loadTrips();
  }

  async loadTrips() {
    const trips = await this.supabaseService.getCompletedTrips();
    this.dataSource = new MatTableDataSource(trips);
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }
}
